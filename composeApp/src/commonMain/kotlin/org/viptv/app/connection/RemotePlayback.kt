@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.SourceCandidate

internal expect fun playbackClientFacts(): JsonObject

internal suspend fun RemoteConnection.sources(card: Card, publish: (List<SourceCandidate>) -> Unit = {}): List<SourceCandidate> = withTimeout(30_000) {
    val started = call("/api/v2/streams", HttpMethod.Post, buildJsonObject {
        put("type", card.identity.kind); put("id", card.identity.id)
        card.identity.ids.firstOrNull { it.namespace == "imdb" }?.let { put("imdb_id", it.value) }
        card.identity.episode?.let { coordinates ->
            coordinates.season?.let { put("season", it) }; put("episode", coordinates.episode)
        }
    }).jsonObject
    val job = started.getValue("id").jsonPrimitive.content.encodeURLPathPart()
    val found = linkedMapOf<String, SourceCandidate>()
    var after = 0
    while (true) {
        val page = call("/api/v2/streams/$job?after=$after&wait_ms=10000").jsonObject
        val events = page.getValue("events").jsonArray
        events.forEach { raw ->
            val event = raw.jsonObject
            event["streams"]?.jsonArray.orEmpty().forEach { stream ->
                val value = stream.jsonObject
                val id = value.getValue("id").jsonPrimitive.content
                found[id] = SourceCandidate(id, event["source"]?.jsonPrimitive?.content ?: "server",
                    value["name"]?.jsonPrimitive?.contentOrNull ?: value["title"]?.jsonPrimitive?.contentOrNull ?: "Source",
                    detail = value["detail"]?.jsonPrimitive?.contentOrNull,
                    quality_height = value["quality_height"]?.jsonPrimitive?.longOrNull,
                    audio_languages = value["audio_languages"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull })
            }
        }
        after += events.size
        if (events.isNotEmpty()) publish(found.values.toList())
        if (page["done"]?.jsonPrimitive?.boolean == true) break
        if (events.isEmpty()) delay(500)
    }
    found.values.toList()
}

internal class RemotePlaybackLease(val input: PlaybackInput, val renewSeconds: Long, private val remote: RemoteConnection, val gateway: Boolean = true, val reopensForSeek: Boolean = gateway) {
    private val path = "/api/v2/playback/${input.id.encodeURLPathPart()}"
    suspend fun firstFrame() { if (gateway) remote.call("$path/first-frame", HttpMethod.Post, buildJsonObject {}) }
    suspend fun renew() { remote.call("$path/heartbeat", HttpMethod.Post) }
    suspend fun close() { remote.call(path, HttpMethod.Delete) }
}

internal suspend fun RemoteConnection.openPlayback(source: SourceCandidate, profile: String, clientFacts: JsonObject = playbackClientFacts(), forceGateway: Boolean = false, positionMillis: Long? = null): RemotePlaybackLease {
    val requestId = kotlin.uuid.Uuid.random().toString()
    var playbackId: String? = null
    try {
        return withTimeout(60_000) {
            var response = call("/api/v2/playback", HttpMethod.Post, buildJsonObject {
                put("request_id", requestId); put("stream_id", source.id)
                put("client", clientFacts)
                put("force_gateway", forceGateway || clientFacts["can_play_direct"]?.jsonPrimitive?.booleanOrNull != true)
                put("position", (positionMillis ?: 0).coerceAtLeast(0) / 1000.0); put("resume", positionMillis == null)
            }).jsonObject
            playbackId = response.getValue("id").jsonPrimitive.content
            while (response.getValue("status").jsonPrimitive.content != "ready") {
                if (response.getValue("status").jsonPrimitive.content in listOf("failed", "stopped", "expired"))
                    throw RemoteFailure(502, "This source could not play through the server gateway.")
                delay(250)
                response = call("/api/v2/playback/${requireNotNull(playbackId).encodeURLPathPart()}").jsonObject
            }
            val delivery = response.getValue("delivery").jsonObject
            val kind = delivery.getValue("kind").jsonPrimitive.content
            require(kind == "gateway" || (kind == "direct" && clientFacts["can_play_direct"]?.jsonPrimitive?.booleanOrNull == true))
            val headers = delivery["headers"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
            val input = PlaybackInput(requireNotNull(playbackId), delivery.getValue("url").jsonPrimitive.content,
                headers = headers, positionMillis = ((delivery["position"]?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1000).toLong(), profileId = profile, live = delivery["live"]?.jsonPrimitive?.booleanOrNull ?: response["live"]?.jsonPrimitive?.booleanOrNull ?: false,
                timelineOffsetMillis = ((delivery["timeline_offset_seconds"]?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1000).toLong(),
                durationMillis = delivery["duration"]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && it > 0 }?.let { (it * 1000).toLong() })
            RemotePlaybackLease(input, response["renew_after_seconds"]?.jsonPrimitive?.longOrNull ?: 20, this@openPlayback, gateway = kind == "gateway", reopensForSeek = kind == "gateway" && delivery["format"]?.jsonPrimitive?.contentOrNull != "mp4")
        }
    } catch (error: Exception) {
        withContext(NonCancellable) {
            withTimeoutOrNull(5_000) {
                try {
                    playbackId?.let { call("/api/v2/playback/${it.encodeURLPathPart()}", HttpMethod.Delete) }
                        ?: call("/api/v2/playback-requests/${requestId.encodeURLPathPart()}", HttpMethod.Delete)
                } catch (_: Exception) { }
            }
        }
        throw error
    }
}
