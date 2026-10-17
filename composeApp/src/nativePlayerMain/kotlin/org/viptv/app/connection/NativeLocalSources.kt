package org.viptv.app.connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

internal data class NativePlaybackOrigin(val profile: String, val revision: Long, val origin: org.viptv.engine.wire.MetadataOrigin?, val mediaContext: JsonObject, val openedAt: Long = System.nanoTime())

internal suspend fun NativeLocalConnection.fetchLocalSources(profile: String, card: Card,
    publish: suspend (List<SourceCandidate>) -> Unit = {}): List<SourceCandidate> {
    var operation: String? = null
    try {
        // Capture ownership inside IO: cancellation on the return hop must still
        // close the operation that the synchronous engine may already have made.
        val request = withContext(Dispatchers.IO) {
            val planned = if (card.metadata_origin != null) engine.planSourcesForOrigin(profile, card.identity.kind, card.identity.id, json.encodeToString(card))
                else engine.planSources(profile, card.identity.kind, card.identity.id)
            json.parseToJsonElement(planned).jsonObject.also {
                operation = it.getValue("operation").jsonPrimitive.content
            }
        }
        val id = requireNotNull(operation)
        val capacity = Semaphore(3)
        val gate = Mutex()
        var found = emptyList<SourceCandidate>()
        coroutineScope {
            request.getValue("requests").jsonArray.map { item -> async {
                val connection = item.jsonObject
                val body = try {
                    capacity.withPermit { withTimeout(10_000) { addonJson(client, connection.getValue("url").jsonPrimitive.content) } }
                } catch (_: TimeoutCancellationException) { null }
                catch (error: CancellationException) { throw error }
                catch (_: Exception) { null }
                val response = buildJsonObject {
                    put("provider", connection.getValue("provider")); body?.let { put("body", it) }
                }
                gate.withLock {
                    val partial = withContext(Dispatchers.IO) {
                        json.decodeFromString<List<SourceCandidate>>(engine.projectSourcesPartial(id, JsonArray(listOf(response)).toString(), false))
                    }
                    currentCoroutineContext().ensureActive()
                    if (partial.map { it.id } != found.map { it.id }) {
                        found = partial
                        publish(partial)
                    }
                }
            } }.awaitAll()
        }
        currentCoroutineContext().ensureActive()
        return withContext(Dispatchers.IO) {
            json.decodeFromString(engine.projectSourcesPartial(id, "[]", true))
        }
    } finally {
        operation?.let { id -> withContext(NonCancellable) {
            withContext(Dispatchers.IO) {
                // Closing discovery retains choices already handed to the player.
                try { engine.cancelSourceDiscovery(profile, id) } catch (_: Exception) { }
            }
        } }
    }
}

internal suspend fun NativeLocalConnection.fetchLocalSubtitles(profile: String, card: Card): org.viptv.engine.wire.SubtitleSnapshot {
        val prepared = prepareMetadataCard(profile, card)
        val raw = withContext(Dispatchers.IO) {
            if (prepared.metadata_origin != null) engine.planSubtitlesForOrigin(profile, prepared.identity.kind, prepared.identity.id, "{}", json.encodeToString(prepared))
            else engine.planSubtitles(profile, prepared.identity.kind, prepared.identity.id, "{}")
        }
        val plan = json.parseToJsonElement(raw).jsonObject
        val capacity = Semaphore(3)
        val results = coroutineScope {
            plan.getValue("requests").jsonArray.map { item -> async {
                val request = item.jsonObject
                val url = request["url"]?.jsonPrimitive?.contentOrNull
                val body = try { url?.let { capacity.withPermit { withTimeout(10_000) { addonJson(client, it) } } } }
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
        val playback = playbackOrigins[input.id] ?: throw LocalFailure("This playback session has expired.")
        val selected = withContext(Dispatchers.IO) { json.parseToJsonElement(engine.openSubtitle(id)).jsonObject }
        val origin = selected["metadata_origin"]?.let { json.decodeFromJsonElement<org.viptv.engine.wire.MetadataOrigin?>(it) }
        if (playback.profile != input.profileId || playback.revision != input.revision || playback.origin != origin || playback.mediaContext != selected.getValue("media_context").jsonObject)
            throw LocalFailure("This subtitle belongs to a different title or add-on.")

        if (selected.getValue("profile_id").jsonPrimitive.content != input.profileId ||
            selected.getValue("revision").jsonPrimitive.long != input.revision) throw LocalFailure("This subtitle choice has expired.")
        val content = withTimeout(10_000) { addonBytes(client, selected.getValue("url").jsonPrimitive.content) }
        // Recheck after network IO so a profile/configuration change cannot attach stale content.
        withContext(Dispatchers.IO) { engine.openSubtitle(id) }
        if (playbackOrigins[input.id] !== playback) throw LocalFailure("This playback session has changed.")
        return content
    }

internal suspend fun NativeLocalConnection.fetchLocalOpenSource(id: String): PlaybackInput = withContext(Dispatchers.IO) {
        val source = json.parseToJsonElement(engine.openSource(id)).jsonObject
        val context = NativePlaybackOrigin(source.getValue("profile_id").jsonPrimitive.content, source.getValue("revision").jsonPrimitive.long,
            source["metadata_origin"]?.let { json.decodeFromJsonElement<org.viptv.engine.wire.MetadataOrigin?>(it) }, source.getValue("media_context").jsonObject)
        playbackOrigins[id] = context
        if (playbackOrigins.size > 64) playbackOrigins.entries.minByOrNull { it.value.openedAt }?.let { playbackOrigins.remove(it.key, it.value) }
        val headers = source.getValue("headers").jsonArray.associate { pair ->
            val values=pair.jsonArray;values[0].jsonPrimitive.content to values[1].jsonPrimitive.content
        }
        PlaybackInput(id,source.getValue("url").jsonPrimitive.content,headers,source.getValue("position_millis").jsonPrimitive.long,source.getValue("profile_id").jsonPrimitive.content,source.getValue("revision").jsonPrimitive.long,
            live = source["live"]?.jsonPrimitive?.booleanOrNull ?: false,
            requiredAudioIndex = source.requiredAudioIndex())
    }
