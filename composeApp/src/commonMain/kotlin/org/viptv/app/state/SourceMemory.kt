package org.viptv.app.state

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

// Facts of a source that started, never its id or URL: both expire, so a remembered choice is re-resolved from a fresh discovery.
@Serializable
internal data class RememberedSource(
    val provider_id: String,
    val languages: List<String> = emptyList(),
)

@Serializable
internal data class SourceMemoryRecord(val entries: Map<String, RememberedSource> = emptyMap(),
    val affinity: Map<String, String> = emptyMap())

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

    fun useDeviceStorage() { store = platformSourceMemoryStore() }

    internal fun book(): SourceMemoryBook = SourceMemoryBook(store)
}

internal enum class SourceChoice { None, Manual }

internal class SourceMemoryBook(private val store: SourceMemoryStore = EmptySourceMemoryStore) {
    private var record = decodeSourceMemory(runCatching { store.read() }.getOrNull())

    fun entry(profile: String, card: Card): RememberedSource? = record.entries[sourceMemoryKey(profile, card)]

    fun remember(profile: String, card: Card, source: SourceCandidate) {
        val key = sourceMemoryKey(profile, card)
        val remembered = RememberedSource(source.provider_id,
            source.audio_languages.map(String::trim).filter(String::isNotEmpty).distinct().take(MAX_LANGUAGES))
        val merged = record.entries - key + (key to remembered)
        val entries = if (merged.size <= MAX_ENTRIES) merged
            else merged.entries.drop(merged.size - MAX_ENTRIES).associate { it.key to it.value }
        record = record.copy(entries = entries)
        runCatching { store.write(encodeSourceMemory(record)) }
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
        runCatching { store.write(encodeSourceMemory(record)) }
    }

    fun forget(profile: String, card: Card) {
        val key = sourceMemoryKey(profile, card)
        if (key !in record.entries) return
        record = record.copy(entries = record.entries - key)
        runCatching { store.write(encodeSourceMemory(record)) }
    }

    companion object {
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
    if (choice != SourceChoice.Manual) return
    val profile = mutable.value.selectedProfile ?: return
    val card = mutable.value.sourceTitle ?: return
    val source = mutable.value.playingSource ?: return
    if (card.identity.kind !in rememberedKinds) return
    sourceMemory.remember(profile, card, source)
}
