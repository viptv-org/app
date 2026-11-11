package org.viptv.app.state

import androidx.compose.runtime.*
import kotlin.concurrent.Volatile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.viptv.app.connection.PreferencesSnapshot
import org.viptv.app.connection.RankedSources
import org.viptv.app.connection.failsHardFilter
import org.viptv.app.player.canonicalLanguageTag
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.SourceCandidate

// Only presentation.source_id is durable; candidate IDs and URLs expire.
@Serializable
internal data class RememberedSource(
    val provider_id: String,
    val languages: List<String> = emptyList(),
    val provider_name: String? = null,
    val source_id: String? = null,
)

@Serializable
internal data class SourceMemoryRecord(
    val entries: Map<String, RememberedSource> = emptyMap(),
    val across: Set<String> = emptySet(),
    val crossTitle: Map<String, RememberedSource> = emptyMap(),
    val lastManual: Map<String, RememberedSource> = emptyMap(),
    val affinity: Map<String, String> = emptyMap(),
)

internal interface SourceMemoryStore {
    fun read(): String?
    fun write(value: String)
}

internal object EmptySourceMemoryStore : SourceMemoryStore {
    override fun read(): String? = null
    override fun write(value: String) = Unit
}

internal expect fun platformSourceMemoryStore(): SourceMemoryStore

private val memoryJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal fun decodeSourceMemory(raw: String?): SourceMemoryRecord = raw
    ?.let { runCatching { memoryJson.decodeFromString(SourceMemoryRecord.serializer(), it) }.getOrNull() }
    ?: SourceMemoryRecord()

internal fun encodeSourceMemory(record: SourceMemoryRecord): String =
    memoryJson.encodeToString(SourceMemoryRecord.serializer(), record)

// An episode is remembered as its series, so the next episode inherits the choice.
internal fun rememberedTitle(card: Card): MediaIdentity {
    val parent = card.parent_identity
    return if (card.identity.kind == "series" && parent?.kind == "series") parent else card.identity
}

internal fun sourceMemoryKey(profile: String, card: Card): String {
    val title = rememberedTitle(card)
    return "$profile\u001F${title.kind}:${title.id}"
}

// Tests build their own books; every platform entry point switches this on before it builds a controller.
object SourceMemory {
    @Volatile private var store: SourceMemoryStore = EmptySourceMemoryStore

    private var shared: SourceMemoryBook? = null

    fun useDeviceStorage() { store = platformSourceMemoryStore(); shared = null }

    internal fun book(): SourceMemoryBook = if (store === EmptySourceMemoryStore) SourceMemoryBook(store)
        else shared ?: SourceMemoryBook(store).also { shared = it }
}

internal enum class SourceChoice { None, Manual }

internal class SourceMemoryBook(private val store: SourceMemoryStore = EmptySourceMemoryStore) {
    private var record by mutableStateOf(decodeSourceMemory(runCatching { store.read() }.getOrNull()))

    var revision: Long = 0
        private set

    fun across(profile: String): Boolean = profile in record.across

    fun setAcross(profile: String, enabled: Boolean) {
        record = record.copy(across = (record.across - profile).let { if (enabled) it + profile else it },
            crossTitle = if (enabled && profile !in record.crossTitle) record.lastManual[profile]?.let { record.crossTitle + (profile to it) } ?: record.crossTitle else record.crossTitle)
        persist()
    }

    fun context(profile: String, card: Card): org.viptv.app.connection.SourceReuseContext =
        org.viptv.app.connection.SourceReuseContext(entry(profile, card)?.wire(),
            record.crossTitle[profile]?.takeIf { across(profile) }?.wire())

    fun choice(profile: String, card: Card): RememberedSource? = entry(profile, card)
        ?: record.crossTitle[profile]?.takeIf { across(profile) }

    private fun persist() {
        revision++
        var encoded = encodeSourceMemory(record)
        while (encoded.length > MAX_RECORD_LENGTH) {
            record = when {
                record.entries.isNotEmpty() -> record.copy(entries = record.entries - record.entries.keys.first())
                record.lastManual.isNotEmpty() -> record.copy(lastManual = record.lastManual - record.lastManual.keys.first())
                record.crossTitle.isNotEmpty() -> record.copy(crossTitle = record.crossTitle - record.crossTitle.keys.first())
                else -> break
            }
            encoded = encodeSourceMemory(record)
        }
        runCatching { store.write(encoded) }
    }

    fun entry(profile: String, card: Card): RememberedSource? = record.entries[sourceMemoryKey(profile, card)]

    fun remember(profile: String, card: Card, source: SourceCandidate) {
        if (!source.provider_id.durableSourceText()) return
        val key = sourceMemoryKey(profile, card)
        val remembered = RememberedSource(source.provider_id,
            source.audio_languages.map(String::trim).filter(String::isNotEmpty).distinct().take(MAX_LANGUAGES),
            source.presentation?.provider_name?.takeIf { it.durableSourceText() }, source.presentation?.source_id?.takeIf { it.durableSourceText() })
        val merged = record.entries - key + (key to remembered)
        val entries = if (merged.size <= MAX_ENTRIES) merged
            else merged.entries.drop(merged.size - MAX_ENTRIES).associate { it.key to it.value }
        record = record.copy(entries = entries, lastManual = record.lastManual + (profile to remembered),
            crossTitle = if (across(profile)) record.crossTitle + (profile to remembered) else record.crossTitle)
        persist()
    }

    // One provider per profile: the last start that reached a presented frame, manual or automatic.
    fun affinity(profile: String): String? = record.affinity[profile]

    fun rememberAffinity(profile: String, providerId: String) {
        val provider = providerId.trim().takeIf { it.isNotEmpty() && it.length <= MAX_AFFINITY_LENGTH && it.none(Char::isISOControl) }
            ?: return
        if (record.affinity[profile] == provider) return
        val merged = record.affinity - profile + (profile to provider)
        val affinity = if (merged.size <= MAX_PROFILES) merged
            else merged.entries.drop(merged.size - MAX_PROFILES).associate { it.key to it.value }
        record = record.copy(affinity = affinity)
        persist()
    }

    fun forget(profile: String, card: Card) {
        val key = sourceMemoryKey(profile, card)
        if (key !in record.entries) return
        record = record.copy(entries = record.entries - key)
        persist()
    }

    companion object {
        const val MAX_RECORD_LENGTH = 8_000
        const val MAX_ENTRIES = 200
        const val MAX_LANGUAGES = 8
        const val MAX_PROFILES = 16
        const val MAX_AFFINITY_LENGTH = 128
    }
}

// The remembered add-on wins, then any other row in the remembered audio; a row with no matching audio is never a substitute.
internal fun RankedSources.matchingSource(memory: RememberedSource): SourceCandidate? {
    val eligible = rows.filterNot { it.failsHardFilter() }
    val sameProvider = eligible.filter { it.provider_id == memory.provider_id }
    val wanted = memory.languages.mapNotNull(::languageKey).toSet()
    return sameProvider.firstOrNull { it.speaks(wanted) }
        ?: eligible.firstOrNull { it.speaks(wanted) }
        ?: if (wanted.isEmpty()) sameProvider.firstOrNull() else null
}

private fun SourceCandidate.speaks(wanted: Set<String>): Boolean =
    wanted.isNotEmpty() && audio_languages.any { languageKey(it) in wanted }

internal fun languageKey(tag: String): String? = tag.trim().takeIf(String::isNotEmpty)
    ?.let { runCatching { canonicalLanguageTag(it) }.getOrNull() }
    ?.substringBefore('-')?.takeIf(String::isNotEmpty)

internal val rememberedKinds = setOf("movie", "series")

internal fun PreferencesSnapshot?.rememberedSourceReuseEnabled(): Boolean =
    this?.preferences?.remembered_source_reuse != false

internal fun ApplicationController.rememberProviderAffinity(profile: String) {
    val provider = mutable.value.playingSource?.provider_id ?: return
    sourceMemory.rememberAffinity(profile, provider)
}

internal fun ApplicationController.rememberSourceChoice() {
    val choice = sourceChoice
    sourceChoice = SourceChoice.None
    if (choice != SourceChoice.Manual || !mutable.value.preferences.rememberedSourceReuseEnabled()) return
    val profile = mutable.value.selectedProfile ?: return
    val card = mutable.value.sourceTitle ?: return
    val source = mutable.value.playingSource ?: return
    if (card.identity.kind !in rememberedKinds) return
    val language = mutable.value.playback?.selectedAudioLanguage
    sourceMemory.remember(profile, card, source.copy(audio_languages = language?.let(::listOf)
        ?: source.audio_languages.takeIf { it.size == 1 }.orEmpty()))
}

internal fun RememberedSource.wire() = org.viptv.app.connection.SourceReuseMemory(provider_id, languages, provider_name, source_id)

private fun String.durableSourceText(): Boolean = isNotBlank() && length <= 256 && !contains("://") && none { it.isISOControl() }
