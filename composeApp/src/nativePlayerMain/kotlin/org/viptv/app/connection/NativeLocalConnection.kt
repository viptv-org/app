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

internal class NativeLocalConnection(path: String, secrets: uniffi.viptv_core.SecretStore = platformSecrets(),
    private val client: io.ktor.client.HttpClient = platformClient()) : LocalConnection {
    private val engine = LocalEngine(path, secrets)
    private val json = Json { ignoreUnknownKeys = true }
    private val pendingHomes=ConcurrentHashMap<String,String>()

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
        try { engine.deleteProfile(id, pin) }
        catch (_: Exception) { throw LocalFailure("Could not delete the profile. Check its PIN; at least one profile must remain.") }
    }
    override suspend fun setProfilePin(id: String, oldPin: String, newPin: String) = withContext(Dispatchers.IO) {
        try { engine.setProfilePin(id, oldPin, newPin) }
        catch (_: Exception) { throw LocalFailure("Could not update the PIN. Use 4–8 digits and check the current PIN.") }
    }
    override suspend fun verifyProfilePin(id: String, pin: String): Boolean = withContext(Dispatchers.IO) { engine.verifyProfilePin(id, pin) }

    override suspend fun refreshHome(profile: String): HomeSnapshot = runPlan(pendingHomes.remove(profile) ?: withContext(Dispatchers.IO) { engine.planHome(profile) })

    override suspend fun discoveryControls(profile: String, type: String): DiscoveryControls = withContext(Dispatchers.IO) {
        json.decodeFromString(engine.discoveryControls(profile, type))
    }
    override suspend fun browse(profile: String, query: BrowseQuery): CatalogPage {
        val plan = withContext(Dispatchers.IO) { json.parseToJsonElement(engine.planDiscover(profile, json.encodeToString(query))).jsonObject }
        val requests = plan.getValue("requests").jsonArray
        val results = fetchLocalCatalogRows(requests) { addonJson(client, it) }
        if (requests.isNotEmpty() && results.all { it.jsonObject["body"] == null || it.jsonObject["body"] == JsonNull })
            throw LocalFailure("Could not load this catalog. Check your connection and try again.")
        return withContext(Dispatchers.IO) { json.decodeFromString(engine.projectDiscover(plan.getValue("id").jsonPrimitive.content, results.toString())) }
    }

    override suspend fun library(profile: String): List<Card> = withContext(Dispatchers.IO) {
        val cards = mutableListOf<Card>()
        var offset = 0u
        do {
            val page = json.decodeFromString<CatalogPage>(engine.library(profile, offset, 100u))
            cards += page.cards
            offset += page.cards.size.toUInt()
            if (page.complete || page.cards.isEmpty()) break
        } while (true)
        cards
    }
    override suspend fun saveFavorite(profile: String, card: Card) = withContext(Dispatchers.IO) {
        val revision = json.decodeFromString<ProfileConfiguration>(engine.profile(profile)).scope.revision.toULong()
        engine.saveFavorite(profile, revision, json.encodeToString(card))
    }
    override suspend fun removeFavorite(profile: String, card: Card) = withContext(Dispatchers.IO) {
        val revision = json.decodeFromString<ProfileConfiguration>(engine.profile(profile)).scope.revision.toULong()
        engine.removeFavorite(profile, revision, card.identity.kind, card.identity.id)
    }

    override suspend fun addons(): List<IntegrationConnection> = withContext(Dispatchers.IO) {
        json.decodeFromString(engine.addons())
    }

    override suspend fun enabledAddons(profile: String): Set<String> = withContext(Dispatchers.IO) {
        json.decodeFromString<ProfileConfiguration>(engine.profile(profile)).assignments.filter { it.enabled.isNotEmpty() }.map { it.connection_id }.toSet()
    }
    override suspend fun setAddonEnabled(profile: String, id: String, enabled: Boolean) = withContext(Dispatchers.IO) { engine.setAddonEnabled(profile,id,enabled) }
    override suspend fun removeAddon(id: String) = withContext(Dispatchers.IO) { engine.removeAddon(id) }

    override suspend fun installAddon(profile: String, manifestUrl: String): List<IntegrationConnection> {
        val url = try { withContext(Dispatchers.IO) { engine.checkAddonUrl(manifestUrl.trim()) } }
        catch (_: Exception) { throw LocalFailure("Enter a valid add-on manifest URL.") }
        val manifest = addonJson(client, url)
        try { withContext(Dispatchers.IO) { engine.installAddon(profile, url, manifest) } }
        catch (_: Exception) { throw LocalFailure("The add-on could not be installed. Check the manifest and secure storage.") }
        return addons()
    }

    override suspend fun search(profile: String, term: String): CatalogPage {
        val snapshot = runPlan(withContext(Dispatchers.IO) { engine.planSearch(profile, term) })
        return CatalogPage(snapshot.rows.flatMap { it.cards }, null, true)
    }

    override suspend fun detail(profile: String, card: Card, offset: Int): DetailSnapshot {
        val identity = card.parent_identity ?: card.identity
        val plan = try { withContext(Dispatchers.IO) { engine.planDetail(profile, identity.kind, identity.id, card.provider_id) } }
        catch (_: Exception) { throw LocalFailure("This title is unavailable from your enabled add-ons.") }
        val operation = json.parseToJsonElement(plan).jsonObject
        val body = addonJson(client, operation.getValue("url").jsonPrimitive.content)
        return try { withContext(Dispatchers.IO) {
            json.decodeFromString(engine.projectDetail(operation.getValue("operation").jsonPrimitive.content, body, offset.toUInt()))
        } } catch (_: Exception) { throw LocalFailure("The add-on returned invalid title details.") }
    }

    override suspend fun sources(profile: String, card: Card): List<SourceCandidate> {
        val plan = withContext(Dispatchers.IO) { engine.planSources(profile, card.identity.kind, card.identity.id) }
        val request = json.parseToJsonElement(plan).jsonObject
        val capacity = Semaphore(3)
        val results = coroutineScope {
            request.getValue("requests").jsonArray.map { item -> async {
                val connection = item.jsonObject
                val body = try { capacity.withPermit { withTimeout(2_000) { addonJson(client, connection.getValue("url").jsonPrimitive.content) } } }
                catch (_: TimeoutCancellationException) { null }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { null }
                buildJsonObject { put("provider", connection.getValue("provider").jsonPrimitive.content);body?.let { put("body",it) } }
            } }.map { it.await() }
        }
        return withContext(Dispatchers.IO) {
            json.decodeFromString(engine.projectSources(request.getValue("operation").jsonPrimitive.content, JsonArray(results).toString()))
        }
    }

    override suspend fun subtitles(profile: String, card: Card): org.viptv.engine.wire.SubtitleSnapshot {
        val raw = withContext(Dispatchers.IO) { engine.planSubtitles(profile, card.identity.kind, card.identity.id, "{}") }
        val plan = json.parseToJsonElement(raw).jsonObject
        val capacity = Semaphore(3)
        val results = coroutineScope {
            plan.getValue("requests").jsonArray.map { item -> async {
                val request = item.jsonObject
                val url = request["url"]?.jsonPrimitive?.contentOrNull
                val body = try { url?.let { capacity.withPermit { withTimeout(2_000) { addonJson(client, it) } } } }
                catch (_: TimeoutCancellationException) { null }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { null }
                buildJsonObject { put("provider", request.getValue("provider")); body?.let { put("body", it) } }
            } }.map { it.await() }
        }
        return withContext(Dispatchers.IO) {
            json.decodeFromString(engine.projectSubtitles(plan.getValue("operation").jsonPrimitive.content, JsonArray(results).toString()))
        }
    }

    override suspend fun subtitleContent(id: String, input: PlaybackInput): ByteArray {
        val selected = withContext(Dispatchers.IO) { json.parseToJsonElement(engine.openSubtitle(id)).jsonObject }
        if (selected.getValue("profile_id").jsonPrimitive.content != input.profileId ||
            selected.getValue("revision").jsonPrimitive.long != input.revision) throw LocalFailure("This subtitle choice has expired.")
        val content = withTimeout(10_000) { addonBytes(client, selected.getValue("url").jsonPrimitive.content) }
        // Recheck after network IO so a profile/configuration change cannot attach stale content.
        withContext(Dispatchers.IO) { engine.openSubtitle(id) }
        return content
    }

    override suspend fun openSource(id: String): PlaybackInput = withContext(Dispatchers.IO) {
        val source = json.parseToJsonElement(engine.openSource(id)).jsonObject
        val headers = source.getValue("headers").jsonArray.associate { pair ->
            val values=pair.jsonArray;values[0].jsonPrimitive.content to values[1].jsonPrimitive.content
        }
        PlaybackInput(id,source.getValue("url").jsonPrimitive.content,headers,source.getValue("position_millis").jsonPrimitive.long,source.getValue("profile_id").jsonPrimitive.content,source.getValue("revision").jsonPrimitive.long)
    }

    override suspend fun recordProgress(input: PlaybackInput, card: Card, positionMillis: Long, durationMillis: Long?) = withContext(Dispatchers.IO) {
        val profile = input.profileId ?: throw LocalFailure("No profile owns this playback session.")
        engine.recordProgress(profile,input.revision.toULong(),json.encodeToString(card),positionMillis.coerceAtLeast(0).toULong(),(durationMillis ?: 0).coerceAtLeast(0).toULong())
    }

    override suspend fun history(profile: String): List<HistoryDisplay> = withContext(Dispatchers.IO) {
        json.decodeFromString<List<HistoryEntry>>(engine.history(profile)).map { entry ->
            HistoryDisplay(entry,historyDayLabel(entry.updated_at_millis))
        }
    }

    override suspend fun clearHistory(profile: String) = withContext(Dispatchers.IO) { engine.clearHistory(profile) }

    private suspend fun runPlan(raw: String): HomeSnapshot {
        val plan = json.parseToJsonElement(raw).jsonObject
        val requests = plan.getValue("requests").jsonArray
        val results = fetchLocalCatalogRows(requests) { addonJson(client, it) }
        return withContext(Dispatchers.IO) {
            json.decodeFromString(engine.projectHome(plan.getValue("id").jsonPrimitive.content, results.toString()))
        }
    }
    override fun close() { pendingHomes.clear(); client.close(); engine.close() }
}
