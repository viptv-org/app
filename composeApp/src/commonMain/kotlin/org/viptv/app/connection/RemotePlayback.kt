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

internal class PreparedPlaybackLease(
    val id: String,
    val renewSeconds: Long,
    private val remote: RemoteConnection,
    private val profile: String,
    private val admission: PlaybackAdmission,
) {
    private val path = "/api/playback/${id.encodeURLPathPart()}"
    suspend fun renew() { remote.call("$path/heartbeat", HttpMethod.Post) }
    suspend fun release() { remote.call(path, HttpMethod.Delete) }
    suspend fun promote(replaces: String?): RemotePlaybackLease =
        remote.promotePreparedPlayback(id, profile, admission, replaces)
}

internal class PlaybackAdmission(val request: JsonObject, val native: LocalTorrents?, val canPlayDirect: Boolean)

private suspend fun RemoteConnection.playbackAdmission(source: SourceCandidate, clientFacts: JsonObject, forceGateway: Boolean, positionMillis: Long?, torrents: LocalTorrents?): PlaybackAdmission {
    val requestId = kotlin.uuid.Uuid.random().toString()
    val negotiatedFacts = if (requiredAudioIndexSupported) clientFacts
        else JsonObject(clientFacts - "supports_required_audio_index")
    val canPlayDirect = clientFacts["can_play_direct"]?.jsonPrimitive?.booleanOrNull == true
    val native = torrents?.takeIf { canPlayDirect && it.negotiate(this) }
    val request = buildJsonObject {
        put("request_id", requestId); put("stream_id", source.id)
        put("client", if (native == null) negotiatedFacts else JsonObject(negotiatedFacts + ("native_torrent" to native.capability)))
        put("force_gateway", !canPlayDirect && (forceGateway || clientFacts["can_play_direct"]?.jsonPrimitive?.booleanOrNull != true))
        put("position", (positionMillis ?: 0).coerceAtLeast(0) / 1000.0); put("resume", positionMillis == null)
    }
    return PlaybackAdmission(request, native, canPlayDirect)
}

internal suspend fun RemoteConnection.openPlayback(source: SourceCandidate, profile: String, clientFacts: JsonObject = playbackClientFacts(), forceGateway: Boolean = false, positionMillis: Long? = null,
    torrents: LocalTorrents? = localTorrents(), stage: (String) -> Unit = {}): RemotePlaybackLease {
    val admission = playbackAdmission(source, clientFacts, forceGateway, positionMillis, torrents)
    val requestId = admission.request.getValue("request_id").jsonPrimitive.content
    var playbackId: String? = null
    try {
        return withTimeout(if (admission.native == null) 60_000 else 150_000) {
            val response = call("/api/playback", HttpMethod.Post, admission.request).jsonObject
            playbackId = response.getValue("id").jsonPrimitive.content
            val ready = awaitReadyPlayback(requireNotNull(playbackId), response)
            playbackLease(requireNotNull(playbackId), profile, admission, ready, stage)
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

internal suspend fun RemoteConnection.preparePlayback(source: SourceCandidate, profile: String, clientFacts: JsonObject = playbackClientFacts(), forceGateway: Boolean = false,
    positionMillis: Long? = null, torrents: LocalTorrents? = localTorrents()): PreparedPlaybackLease {
    val admission = playbackAdmission(source, clientFacts, forceGateway, positionMillis, torrents)
    val request = buildJsonObject {
        admission.request.forEach { (key, value) -> put(key, value) }
        put("prepare", true)
    }
    val response = call("/api/playback", HttpMethod.Post, request).jsonObject
    val id = response.getValue("id").jsonPrimitive.content
    if (response.getValue("status").jsonPrimitive.content != "prepared")
        throw RemoteFailure(502, "This source could not prepare the next episode.")
    return PreparedPlaybackLease(id, response["renew_after_seconds"]?.jsonPrimitive?.longOrNull ?: 20, this, profile, admission)
}

private suspend fun RemoteConnection.promotePreparedPlayback(id: String, profile: String, admission: PlaybackAdmission, replaces: String?): RemotePlaybackLease {
    val body = replaces?.let { buildJsonObject { put("replaces", it) } }
    val response = call("/api/playback/${id.encodeURLPathPart()}/promote", HttpMethod.Post, body).jsonObject
    val ready = awaitReadyPlayback(id, response)
    return playbackLease(id, profile, admission, ready) { }
}

private suspend fun RemoteConnection.awaitReadyPlayback(id: String, first: JsonObject): JsonObject {
    var response = first
    while (true) {
        when (response.getValue("status").jsonPrimitive.content) {
            "ready" -> return response
            "starting" -> {}
            else -> throw RemoteFailure(502, "This source could not play through the server gateway.")
        }
        delay(250)
        response = call("/api/playback/${id.encodeURLPathPart()}").jsonObject
    }
}

private suspend fun RemoteConnection.playbackLease(playbackId: String, profile: String, admission: PlaybackAdmission, response: JsonObject, stage: (String) -> Unit): RemotePlaybackLease {
    val delivery = response.getValue("delivery").jsonObject
    val kind = delivery.getValue("kind").jsonPrimitive.content
    if (kind == "native_torrent" && admission.native != null) {
        val local = admission.native.acquire(this, playbackId, admission.request, stage)
        val input = PlaybackInput(playbackId, local.mediaUrl, positionMillis = (local.positionSeconds * 1000).toLong(), profileId = profile, localTorrent = true)
        return RemotePlaybackLease(input, response["renew_after_seconds"]?.jsonPrimitive?.longOrNull ?: 20, this,
            gateway = false, reopensForSeek = false, local = local)
    }
    if (kind != "gateway" && !(kind == "direct" && admission.canPlayDirect))
        throw RemoteFailure(502, "This source needs a server gateway that this device does not use.")
    val headers = delivery["headers"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
    val input = PlaybackInput(playbackId, delivery.getValue("url").jsonPrimitive.content,
        mimeType = org.viptv.app.player.playbackMimeType(delivery["format"]?.jsonPrimitive?.contentOrNull), headers = headers, positionMillis = ((delivery["position"]?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1000).toLong(), profileId = profile, live = delivery["live"]?.jsonPrimitive?.booleanOrNull ?: response["live"]?.jsonPrimitive?.booleanOrNull ?: false,
        timelineOffsetMillis = ((delivery["timeline_offset_seconds"]?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1000).toLong(),
        durationMillis = delivery["duration"]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && it > 0 }?.let { (it * 1000).toLong() },
        requiredAudioIndex = if (kind == "direct") delivery.requiredAudioIndex() else null)
    return RemotePlaybackLease(input, response["renew_after_seconds"]?.jsonPrimitive?.longOrNull ?: 20, this, gateway = kind == "gateway", reopensForSeek = kind == "gateway" && delivery["format"]?.jsonPrimitive?.contentOrNull != "mp4")
}
