package org.viptv.app.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.ProfileConfiguration
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.engine.wire.CatalogPage
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import org.viptv.engine.wire.SourceCandidate
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.HistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import uniffi.viptv_core.LocalEngine
import java.util.concurrent.ConcurrentHashMap

actual fun localConnection(databasePath: String): LocalConnection = NativeLocalConnection(databasePath)

@Serializable
private data class LocalProfileCard(val id: String, val name: String, val locked: Boolean, val avatar_id: String?)

internal open class NativeLocalConnection(path: String, secrets: uniffi.viptv_core.SecretStore = platformSecrets(),
    internal val client: io.ktor.client.HttpClient = platformClient()) : LocalConnection {
    internal val engine = LocalEngine(path, secrets)
    internal val json = Json { ignoreUnknownKeys = true }
    private val pendingHomes=ConcurrentHashMap<String,String>()
    internal val playbackOrigins = ConcurrentHashMap<String, NativePlaybackOrigin>()


    override suspend fun bootstrap(profile: String?): Bootstrap {
        val profiles = withContext(Dispatchers.IO) { json.decodeFromString<List<LocalProfileCard>>(engine.profileCards()) }.map { Profile(it.id, it.name, null, it.locked, it.avatar_id) }
        val home = profile?.let {
            val plan=withContext(Dispatchers.IO){engine.planHome(it)}
            val id=json.parseToJsonElement(plan).jsonObject.getValue("id").jsonPrimitive.content
            pendingHomes[it]=plan
            withContext(Dispatchers.IO){json.decodeFromString<HomeSnapshot>(engine.preparedHome(id))}
        }
        return Bootstrap(profiles, profile, home, addons(), profile?.let { enabledAddons(it) } ?: emptySet())
    }

    override suspend fun createProfile(name: String): Profile = withContext(Dispatchers.IO) {
        val id = java.util.UUID.randomUUID().toString()
        engine.createProfile(id, name)
        Profile(id, name.trim(), null)
    }
    override suspend fun renameProfile(id: String, name: String, pin: String) = withContext(Dispatchers.IO) {
        try { engine.renameProfile(id, name, pin) }
        catch (_: Exception) { throw LocalFailure("Could not rename the profile. Check its name and current PIN.") }
    }
    override suspend fun setProfileAvatar(id: String, avatarId: String, pin: String) = withContext(Dispatchers.IO) {
        try { engine.setProfileAvatar(id, avatarId, pin) }
        catch (_: Exception) { throw LocalFailure("Could not change the avatar. Check the current PIN.") }
    }
    override suspend fun deleteProfile(id: String, pin: String) = withContext(Dispatchers.IO) {
        try { engine.deleteProfile(id, pin); pendingHomes.remove(id); playbackOrigins.entries.removeIf { it.value.profile == id }; Unit }
        catch (_: Exception) { throw LocalFailure("Could not delete the profile. Check its PIN; at least one profile must remain.") }
    }
    override suspend fun setProfilePin(id: String, oldPin: String, newPin: String) = withContext(Dispatchers.IO) {
        try { engine.setProfilePin(id, oldPin, newPin) }
        catch (_: Exception) { throw LocalFailure("Could not update the PIN. Use 4–8 digits and check the current PIN.") }
    }
    override suspend fun verifyProfilePin(id: String, pin: String): Boolean = withContext(Dispatchers.IO) { engine.verifyProfilePin(id, pin) }

    override suspend fun refreshHome(profile: String): HomeSnapshot = enrichHome(profile, runPlan(pendingHomes.remove(profile) ?: withContext(Dispatchers.IO) { engine.planHome(profile) }))

    override suspend fun discoveryControls(profile: String, type: String): DiscoveryControls = fetchLocalDiscoveryControls(profile, type)

    override suspend fun browse(profile: String, query: BrowseQuery): CatalogPage = fetchLocalBrowse(profile, query)

    override suspend fun liveChannels(profile: String, query: LiveQuery): LiveCatalogPage = fetchLiveChannels(profile, query)
    override suspend fun liveGuide(profile: String, channel: String): LiveGuideSnapshot = fetchLiveGuide(profile, channel)
    override suspend fun liveSources(profile: String, channel: String): List<SourceCandidate> = fetchLiveSources(profile, channel)
    override suspend fun liveFavorites(profile: String): Set<String> = withContext(Dispatchers.IO) { decodeLiveFavorites(engine.liveFavorites(profile)) }
    override suspend fun setLiveFavorite(profile: String, channel: String, saved: Boolean): Set<String> = withContext(Dispatchers.IO) { decodeLiveFavorites(engine.setLiveFavorite(profile, channel, saved)) }


    override suspend fun libraryPage(profile: String, query: org.viptv.engine.wire.LibraryQuery): org.viptv.engine.wire.LibrarySnapshot = withContext(Dispatchers.IO) {
        json.decodeFromString(engine.libraryPage(profile, json.encodeToString(query)))
    }
    override suspend fun library(profile: String): List<Card> = fetchLocalLibrary(profile)
    override suspend fun libraryMembership(profile: String): org.viptv.engine.wire.LibraryMembershipSnapshot = withContext(Dispatchers.IO) {
        json.decodeFromString(engine.libraryMembership(profile))
    }


    override suspend fun saveFavorite(profile: String, card: Card): Unit = fetchLocalSaveFavorite(profile, card)

    override suspend fun removeFavorite(profile: String, card: Card): Unit = fetchLocalRemoveFavorite(profile, card)

    override suspend fun addons(): List<IntegrationConnection> = withContext(Dispatchers.IO) {
        json.decodeFromString<List<IntegrationConnection>>(engine.addons()).filter { it.plugin_id == "stremio" }
    }

    override suspend fun enabledAddons(profile: String): Set<String> = withContext(Dispatchers.IO) {
        json.decodeFromString<ProfileConfiguration>(engine.profile(profile)).assignments.filter { it.enabled.isNotEmpty() }.map { it.connection_id }.toSet()
    }
    override suspend fun setAddonEnabled(profile: String, id: String, enabled: Boolean) = withContext(Dispatchers.IO) { engine.setAddonEnabled(profile,id,enabled) }
    override suspend fun removeAddon(id: String) = withContext(Dispatchers.IO) { engine.removeAddon(id) }

    override suspend fun installAddon(profile: String, manifestUrl: String): List<IntegrationConnection> {
        commitAddonInstallation(profile, manifestUrl)
        return addons()
    }

    override suspend fun commitAddonInstallation(profile: String, manifestUrl: String) {
        val url = try { withContext(Dispatchers.IO) { engine.checkAddonUrl(manifestUrl.trim()) } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { throw LocalFailure("Enter a valid add-on manifest URL.") }
        val manifest = addonJson(client, url)
        try { withContext(Dispatchers.IO) { engine.installAddon(profile, url, manifest) } }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { throw LocalFailure("The add-on could not be installed. Check the manifest and secure storage.") }
    }

    override suspend fun search(profile: String, term: String): CatalogPage {
        val snapshot = searchResults(profile, term)
        return CatalogPage(snapshot.rows.flatMap { it.cards }, null, snapshot.complete)
    }

    override suspend fun searchResults(profile: String, term: String): SearchSnapshot =
        nativeSearchSnapshot(runPlan(withContext(Dispatchers.IO) { engine.planSearch(profile, term) }))

    override suspend fun detail(profile: String, card: Card, offset: Int): DetailSnapshot = fetchDetailPage(profile, card, offset, 24)
    override suspend fun detailPage(profile: String, card: Card, offset: Int, limit: Int): DetailSnapshot = fetchDetailPage(profile, card, offset, limit)

    override suspend fun sources(profile: String, card: Card, publish: (List<SourceCandidate>) -> Unit): List<SourceCandidate> = fetchMergedLocalSources(profile, card, publish)

    override suspend fun subtitles(profile: String, card: Card): org.viptv.engine.wire.SubtitleSnapshot = fetchLocalSubtitles(profile, card)

    override suspend fun subtitleContent(id: String, input: PlaybackInput): ByteArray = fetchLocalSubtitleContent(id, input)

    override suspend fun rankSources(profile: String, sources: List<SourceCandidate>): List<SourceCandidate> = withContext(Dispatchers.IO) {
        json.decodeFromString(engine.rankSources(profile, json.encodeToString(sources)))
    }
    override suspend fun openSource(id: String): PlaybackInput = fetchLocalOpenSource(id)

    override suspend fun recordProgress(input: PlaybackInput, card: Card, positionMillis: Long, durationMillis: Long?) = withContext(Dispatchers.IO) {
        val profile = input.profileId ?: throw LocalFailure("No profile owns this playback session.")
        engine.recordProgress(profile,input.revision.toULong(),json.encodeToString(card),positionMillis.coerceAtLeast(0).toULong(),(durationMillis ?: 0).coerceAtLeast(0).toULong())
    }

    override suspend fun history(profile: String): List<HistoryDisplay> = withContext(Dispatchers.IO) {
        json.decodeFromString<List<HistoryEntry>>(engine.history(profile)).map { entry ->
            HistoryDisplay(entry,historyDayLabel(entry.updated_at_millis))
        }
    }

    override suspend fun browsingConfiguration(profile: String): BrowsingConfiguration = withContext(Dispatchers.IO) {
        BrowsingConfiguration(json.decodeFromString(engine.profile(profile)), json.decodeFromString<List<IntegrationConnection>>(engine.addons())
            .filter { it.private_profile_id == null || it.private_profile_id == profile })
    }
    override suspend fun profilePreferences(profile: String): PreferencesSnapshot = withContext(Dispatchers.IO) {
        json.decodeFromString(engine.profilePreferences(profile))
    }
    override suspend fun updateProfilePreferences(profile: String, revision: Long, preferences: ProfilePreferences): PreferencesSnapshot = withContext(Dispatchers.IO) {
        json.decodeFromString(engine.updateProfilePreferences(profile, revision.toULong(), Json { encodeDefaults = true }.encodeToString(preferences)))
    }
    override suspend fun sourceIntegrationSchemas(profile: String): SourceIntegrationSnapshot = readSourceIntegrationSchemas(profile)
    override suspend fun configureSourceIntegration(profile: String, revision: Long, plugin: String, configuration: JsonObject) = saveSourceIntegration(profile, revision, plugin, configuration)
    override suspend fun removeSourceIntegration(profile: String, revision: Long, connection: String) = deleteSourceIntegration(profile, revision, connection)

    override suspend fun clearHistory(profile: String) = withContext(Dispatchers.IO) { engine.clearHistory(profile) }

    private suspend fun runPlan(raw: String): HomeSnapshot {
        val plan = json.parseToJsonElement(raw).jsonObject
        val requests = plan.getValue("requests").jsonArray
        val results = fetchLocalCatalogRows(requests) { addonJson(client, it) }
        return withContext(Dispatchers.IO) {
            json.decodeFromString(engine.projectHome(plan.getValue("id").jsonPrimitive.content, results.toString()))
        }
    }
    override fun close() { playbackOrigins.clear(); pendingHomes.clear(); client.close(); engine.close() }
}
