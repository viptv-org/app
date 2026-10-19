@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.SourceCandidate

internal expect fun playbackClientFacts(): JsonObject


internal class RemotePlaybackLease(val input: PlaybackInput, val renewSeconds: Long, private val remote: RemoteConnection, val gateway: Boolean = true, val reopensForSeek: Boolean = gateway) {
    private val path = "/api/playback/${input.id.encodeURLPathPart()}"
    suspend fun firstFrame() { if (gateway) remote.call("$path/first-frame", HttpMethod.Post, buildJsonObject {}) }
    suspend fun renew() { remote.call("$path/heartbeat", HttpMethod.Post) }
    suspend fun close() { remote.call(path, HttpMethod.Delete) }
}

internal suspend fun RemoteConnection.openPlayback(source: SourceCandidate, profile: String, clientFacts: JsonObject = playbackClientFacts(), forceGateway: Boolean = false, positionMillis: Long? = null): RemotePlaybackLease {
    val requestId = kotlin.uuid.Uuid.random().toString()
    var playbackId: String? = null
    val negotiatedFacts = if (requiredAudioIndexSupported) clientFacts
        else JsonObject(clientFacts - "supports_required_audio_index")
    // R6: a target whose own engine plays the URL must never take a server
    // gateway lease — that lease is what posts first-frames and reopens on seek.
    val directOnly = clientFacts["can_play_direct"]?.jsonPrimitive?.booleanOrNull == true
    try {
        return withTimeout(60_000) {
            var response = call("/api/playback", HttpMethod.Post, buildJsonObject {
                put("request_id", requestId); put("stream_id", source.id)
                put("client", negotiatedFacts)
                put("force_gateway", !directOnly && (forceGateway || clientFacts["can_play_direct"]?.jsonPrimitive?.booleanOrNull != true))
                put("position", (positionMillis ?: 0).coerceAtLeast(0) / 1000.0); put("resume", positionMillis == null)
            }).jsonObject
            playbackId = response.getValue("id").jsonPrimitive.content
            while (response.getValue("status").jsonPrimitive.content != "ready") {
                if (response.getValue("status").jsonPrimitive.content in listOf("failed", "stopped", "expired"))
                    throw RemoteFailure(502, "This source could not play through the server gateway.")
                delay(250)
                response = call("/api/playback/${requireNotNull(playbackId).encodeURLPathPart()}").jsonObject
            }
            val delivery = response.getValue("delivery").jsonObject
            val kind = delivery.getValue("kind").jsonPrimitive.content
            require(if (directOnly) kind == "direct" else kind == "gateway") {
                "This source needs a server gateway that this device does not use."
            }
            val headers = delivery["headers"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
            val input = PlaybackInput(requireNotNull(playbackId), delivery.getValue("url").jsonPrimitive.content,
                mimeType = org.viptv.app.player.playbackMimeType(delivery["format"]?.jsonPrimitive?.contentOrNull), headers = headers, positionMillis = ((delivery["position"]?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1000).toLong(), profileId = profile, live = delivery["live"]?.jsonPrimitive?.booleanOrNull ?: response["live"]?.jsonPrimitive?.booleanOrNull ?: false,
                timelineOffsetMillis = ((delivery["timeline_offset_seconds"]?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1000).toLong(),
                durationMillis = delivery["duration"]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && it > 0 }?.let { (it * 1000).toLong() },
                requiredAudioIndex = if (kind == "direct") delivery.requiredAudioIndex() else null)
            RemotePlaybackLease(input, response["renew_after_seconds"]?.jsonPrimitive?.longOrNull ?: 20, this@openPlayback, gateway = kind == "gateway", reopensForSeek = kind == "gateway" && delivery["format"]?.jsonPrimitive?.contentOrNull != "mp4")
        }
    } catch (error: Exception) {
        withContext(NonCancellable) {
            withTimeoutOrNull(5_000) {
                try {
                    playbackId?.let { call("/api/playback/${it.encodeURLPathPart()}", HttpMethod.Delete) }
                        ?: call("/api/playback-requests/${requestId.encodeURLPathPart()}", HttpMethod.Delete)
                } catch (_: Exception) { }
            }
        }
        throw error
    }
}
