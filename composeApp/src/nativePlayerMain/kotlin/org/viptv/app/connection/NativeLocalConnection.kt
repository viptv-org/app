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
import org.viptv.engine.wire.HomeSnapshot
import org.viptv.engine.wire.ProfileConfiguration
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.engine.wire.CatalogPage
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.DetailSnapshot
import uniffi.viptv_core.LocalEngine

actual fun localConnection(databasePath: String): LocalConnection = NativeLocalConnection(databasePath)

private class NativeLocalConnection(path: String) : LocalConnection {
    private val engine = LocalEngine(path, platformSecrets())
    private val client = platformClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun bootstrap(profile: String?): Bootstrap {
        val profiles = withContext(Dispatchers.IO) { json.decodeFromString<List<ProfileConfiguration>>(engine.profiles()) }.map { Profile(it.scope.profile_id, it.name, null) }
        val home = profile?.let { loadHome(it) }
        return Bootstrap(profiles, profile, home, addons())
    }

    override suspend fun addons(): List<IntegrationConnection> = withContext(Dispatchers.IO) {
        json.decodeFromString(engine.addons())
    }

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
        val plan = try { withContext(Dispatchers.IO) { engine.planDetail(profile, card.identity.kind, card.identity.id, card.provider_id) } }
        catch (_: Exception) { throw LocalFailure("This title is unavailable from your enabled add-ons.") }
        val operation = json.parseToJsonElement(plan).jsonObject
        val body = addonJson(client, operation.getValue("url").jsonPrimitive.content)
        return try { withContext(Dispatchers.IO) {
            json.decodeFromString(engine.projectDetail(operation.getValue("operation").jsonPrimitive.content, body, offset.toUInt()))
        } } catch (_: Exception) { throw LocalFailure("The add-on returned invalid title details.") }
    }

    private suspend fun loadHome(profile: String): HomeSnapshot = runPlan(withContext(Dispatchers.IO) { engine.planHome(profile) })

    private suspend fun runPlan(raw: String): HomeSnapshot {
        val plan = json.parseToJsonElement(raw).jsonObject
        val requests = plan.getValue("requests").jsonArray
        val capacity = Semaphore(3)
        val results = coroutineScope {
            requests.map { item -> async {
                val row = item.jsonObject
                val body = try { capacity.withPermit {
                    val url = row["url"]?.jsonPrimitive?.contentOrNull
                    if (url == null) null else withTimeout(1_900) { addonJson(client, url) }
                } } catch (_: TimeoutCancellationException) { null }
                  catch (error: CancellationException) { throw error }
                  catch (_: Exception) { null }
                buildJsonObject { put("id", row.getValue("id").jsonPrimitive.content); body?.let { put("body", it) } }
            } }.map { it.await() }
        }
        return withContext(Dispatchers.IO) {
            json.decodeFromString(engine.projectHome(plan.getValue("id").jsonPrimitive.content, JsonArray(results).toString()))
        }
    }
    override fun close() { client.close(); engine.close() }
}
