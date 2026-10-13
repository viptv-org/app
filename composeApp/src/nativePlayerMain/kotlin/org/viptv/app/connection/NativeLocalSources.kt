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

internal suspend fun NativeLocalConnection.fetchLocalSources(profile: String, card: Card): List<SourceCandidate> {
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

internal suspend fun NativeLocalConnection.fetchLocalSubtitles(profile: String, card: Card): org.viptv.engine.wire.SubtitleSnapshot {
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

internal suspend fun NativeLocalConnection.fetchLocalSubtitleContent(id: String, input: PlaybackInput): ByteArray {
        val selected = withContext(Dispatchers.IO) { json.parseToJsonElement(engine.openSubtitle(id)).jsonObject }
        if (selected.getValue("profile_id").jsonPrimitive.content != input.profileId ||
            selected.getValue("revision").jsonPrimitive.long != input.revision) throw LocalFailure("This subtitle choice has expired.")
        val content = withTimeout(10_000) { addonBytes(client, selected.getValue("url").jsonPrimitive.content) }
        // Recheck after network IO so a profile/configuration change cannot attach stale content.
        withContext(Dispatchers.IO) { engine.openSubtitle(id) }
        return content
    }

internal suspend fun NativeLocalConnection.fetchLocalOpenSource(id: String): PlaybackInput = withContext(Dispatchers.IO) {
        val source = json.parseToJsonElement(engine.openSource(id)).jsonObject
        val headers = source.getValue("headers").jsonArray.associate { pair ->
            val values=pair.jsonArray;values[0].jsonPrimitive.content to values[1].jsonPrimitive.content
        }
        PlaybackInput(id,source.getValue("url").jsonPrimitive.content,headers,source.getValue("position_millis").jsonPrimitive.long,source.getValue("profile_id").jsonPrimitive.content,source.getValue("revision").jsonPrimitive.long)
    }
