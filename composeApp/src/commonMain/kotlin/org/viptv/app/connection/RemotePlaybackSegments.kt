package org.viptv.app.connection

import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.viptv.app.features.player.TvSkipKind
import org.viptv.app.features.player.TvSkipRange

@Serializable
private data class SegmentsBody(val segments: List<Segment> = emptyList())

@Serializable
private data class Segment(val kind: String = "", @SerialName("start_ms") val startMillis: Long = 0, @SerialName("end_ms") val endMillis: Long = 0)

private val segmentsCodec = Json { ignoreUnknownKeys = true }

internal suspend fun RemoteConnection.playbackSegments(playbackId: String): List<TvSkipRange> {
    val body = try { call("/api/playback/${playbackId.encodeURLPathPart()}/segments") }
    catch (_: RemoteFailure) { return emptyList() }
    return segmentsCodec.decodeFromJsonElement(SegmentsBody.serializer(), body).segments.mapNotNull { segment ->
        val kind = when (segment.kind) {
            "intro" -> TvSkipKind.Intro
            "recap" -> TvSkipKind.Recap
            "credits" -> TvSkipKind.Credits
            else -> return@mapNotNull null
        }
        TvSkipRange(segment.startMillis, segment.endMillis, kind).takeIf { it.endMillis > it.startMillis }
    }
}
