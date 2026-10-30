@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.SourceCandidate

internal expect fun playbackClientFacts(): JsonObject


internal class RemotePlaybackLease(val input: PlaybackInput, val renewSeconds: Long, private val remote: RemoteConnection, val gateway: Boolean = true, val reopensForSeek: Boolean = gateway,
    private val local: LocalTorrentPlayback? = null) {
    private val path = "/api/playback/${input.id.encodeURLPathPart()}"
    suspend fun firstFrame() { if (local != null) local.firstFrame() else if (gateway) remote.call("$path/first-frame", HttpMethod.Post, buildJsonObject {}) }
    suspend fun segments(): List<org.viptv.app.features.player.TvSkipRange> = remote.playbackSegments(input.id)
    suspend fun renew() { if (local != null) local.renew() else remote.call("$path/heartbeat", HttpMethod.Post) }
    suspend fun close() {
        try { local?.close() } finally { remote.call(path, HttpMethod.Delete) }
    }
}

internal suspend fun RemoteConnection.openPlayback(source: SourceCandidate, profile: String, clientFacts: JsonObject = playbackClientFacts(), forceGateway: Boolean = false, positionMillis: Long? = null,
    torrents: LocalTorrents? = localTorrents(), stage: (String) -> Unit = {}): RemotePlaybackLease {
    val requestId = kotlin.uuid.Uuid.random().toString()
    var playbackId: String? = null
    val negotiatedFacts = if (requiredAudioIndexSupported) clientFacts
        else JsonObject(clientFacts - "supports_required_audio_index")
    // A target whose own engine plays the URL must never take a server gateway lease.
    val directOnly = clientFacts["can_play_direct"]?.jsonPrimitive?.booleanOrNull == true
    val native = torrents?.takeIf { directOnly && it.negotiate(this) }
    val request = buildJsonObject {
        put("request_id", requestId); put("stream_id", source.id)
        put("client", if (native == null) negotiatedFacts else JsonObject(negotiatedFacts + ("native_torrent" to native.capability)))
        put("force_gateway", !directOnly && (forceGateway || clientFacts["can_play_direct"]?.jsonPrimitive?.booleanOrNull != true))
        put("position", (positionMillis ?: 0).coerceAtLeast(0) / 1000.0); put("resume", positionMillis == null)
    }
    try {
        // Local torrent acquisition keeps its own 120s budget: this bound only covers admission plus that budget.
        return withTimeout(if (native == null) 60_000 else 150_000) {
            var response = call("/api/playback", HttpMethod.Post, request).jsonObject
            playbackId = response.getValue("id").jsonPrimitive.content
            while (response.getValue("status").jsonPrimitive.content != "ready") {
                if (response.getValue("status").jsonPrimitive.content in listOf("failed", "stopped", "expired"))
                    throw RemoteFailure(502, "This source could not play through the server gateway.")
                delay(250)
                response = call("/api/playback/${requireNotNull(playbackId).encodeURLPathPart()}").jsonObject
            }
            val delivery = response.getValue("delivery").jsonObject
            val kind = delivery.getValue("kind").jsonPrimitive.content
            if (kind == "native_torrent" && native != null) {
                val local = native.acquire(this@openPlayback, requireNotNull(playbackId), request, stage)
                val input = PlaybackInput(requireNotNull(playbackId), local.mediaUrl, positionMillis = (local.positionSeconds * 1000).toLong(), profileId = profile, localTorrent = true)
                return@withTimeout RemotePlaybackLease(input, response["renew_after_seconds"]?.jsonPrimitive?.longOrNull ?: 20, this@openPlayback,
                    gateway = false, reopensForSeek = false, local = local)
            }
            if (kind != if (directOnly) "direct" else "gateway")
                throw RemoteFailure(502, "This source needs a server gateway that this device does not use.")
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
