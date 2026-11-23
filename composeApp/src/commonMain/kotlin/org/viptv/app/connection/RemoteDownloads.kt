@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.json.*
import org.viptv.app.downloads.DownloadDeferred
import org.viptv.app.downloads.DownloadUnavailable
import org.viptv.app.player.playbackMimeType
import org.viptv.engine.wire.SourceCandidate

internal class DownloadDelivery(val url: String, val headers: Map<String, String>, val mimeType: String?, val sizeBytes: Long?)

internal class DownloadSourceUnsupported : Exception()

internal const val DOWNLOAD_RATE_LIMIT_BACKOFF_MILLIS = 10L * 60_000L

internal const val DOWNLOADS_UNSUPPORTED_BY_SERVER = "Your server doesn't support downloads yet. Update the server and try again."

internal suspend fun RemoteConnection.admitDownload(source: SourceCandidate, clientFacts: JsonObject = playbackClientFacts()): DownloadDelivery {
    val request = buildJsonObject {
        put("request_id", kotlin.uuid.Uuid.random().toString()); put("stream_id", source.id)
        put("client", if (requiredAudioIndexSupported) clientFacts else JsonObject(clientFacts - "supports_required_audio_index"))
        put("purpose", "download")
    }
    val response = try { call("/api/playback", HttpMethod.Post, request).jsonObject }
    catch (failure: RemoteFailure) {
        when {
            failure.code == "download_unsupported" -> throw DownloadSourceUnsupported()
            failure.code == "download_rate_limited" -> throw DownloadDeferred(DOWNLOAD_RATE_LIMIT_BACKOFF_MILLIS)
            failure.code == "invalid_playback_request" -> throw DownloadUnavailable(DOWNLOADS_UNSUPPORTED_BY_SERVER)
            else -> throw failure
        }
    }
    if (response["purpose"]?.jsonPrimitive?.contentOrNull != "download") {
        response["id"]?.jsonPrimitive?.contentOrNull?.let { id -> runCatching { call("/api/playback/${id.encodeURLPathPart()}", HttpMethod.Delete) } }
        throw DownloadUnavailable(DOWNLOADS_UNSUPPORTED_BY_SERVER)
    }
    val delivery = response.getValue("delivery").jsonObject
    if (delivery["kind"]?.jsonPrimitive?.contentOrNull != "direct") throw DownloadSourceUnsupported()
    return DownloadDelivery(
        url = delivery.getValue("url").jsonPrimitive.content,
        headers = delivery["headers"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty(),
        mimeType = playbackMimeType(delivery["format"]?.jsonPrimitive?.contentOrNull),
        sizeBytes = (delivery["size"] as? JsonPrimitive)?.longOrNull,
    )
}
