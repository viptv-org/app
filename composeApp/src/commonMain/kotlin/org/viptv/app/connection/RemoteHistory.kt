package org.viptv.app.connection

import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.PlaybackRecord
import org.viptv.engine.wire.HistoryEntry

@Serializable
private data class HistoryItem(
    val card: Card,
    val updated_at_millis: Long,
    val completed_at_millis: Long? = null,
    val position: Double = 0.0,
    val duration: Double = 0.0,
    val watched: Boolean = false,
    val resume_active: Boolean = false,
    val completion_only: Boolean = false,
    val watch_date_known: Boolean = true,
)

private fun HistoryItem.display(): HistoryDisplay =
    HistoryDisplay(HistoryEntry(card, updated_at_millis, completed_at_millis), historyDayLabel(updated_at_millis),
        position, duration, watched, resume_active, completion_only, watch_date_known)

internal suspend fun RemoteConnection.history(profile: String): List<HistoryDisplay> {
    val codec = Json { ignoreUnknownKeys = true }
    val result = mutableListOf<HistoryDisplay>()
    var offset = 0
    do {
        val values = call("/api/profiles/${profile.encodeURLPathPart()}/history?limit=100&offset=$offset")
        val page = codec.decodeFromJsonElement<List<HistoryItem>>(values)
        result += page.map { it.display() }
        offset += page.size
    } while (page.size == 100)
    return result
}

internal suspend fun RemoteConnection.correctProgress(profile: String, display: HistoryDisplay, correction: HistoryCorrection): ProgressCorrection {
    val card = display.entry.card
    val action = when (correction) {
        HistoryCorrection.Watched -> "watched"
        HistoryCorrection.Unwatched -> "unwatched"
        is HistoryCorrection.Position -> "position"
    }
    val body = buildJsonObject {
        put("id", card.identity.id)
        put("type", card.identity.kind)
        put("name", card.title)
        card.poster?.let { put("poster", it) }
        put("duration", display.duration)
        card.identity.episode?.let { episode ->
            card.parent_identity?.let { put("series_id", it.id) }
            episode.season?.let { put("season", it) }
            put("episode", episode.episode)
        }
        put("action", action)
        (correction as? HistoryCorrection.Position)?.let { put("position", it.seconds) }
    }
    val response = call("/api/profiles/${profile.encodeURLPathPart()}/progress/correct", HttpMethod.Put, body).jsonObject
    return ProgressCorrection(
        position = response["position"]?.jsonPrimitive?.double ?: 0.0,
        duration = response["duration"]?.jsonPrimitive?.double ?: display.duration,
        watched = response["watched"]?.jsonPrimitive?.boolean ?: false,
        resumeActive = response["resume_active"]?.jsonPrimitive?.boolean ?: false,
        completionOnly = response["completion_only"]?.jsonPrimitive?.boolean ?: false,
        watchDateKnown = response["watch_date_known"]?.jsonPrimitive?.boolean ?: true,
    )
}

internal suspend fun RemoteConnection.clearHistory(profile: String) {
    call("/api/profiles/${profile.encodeURLPathPart()}/history", HttpMethod.Delete)
}

internal suspend fun RemoteConnection.recordProgress(
    input: PlaybackInput,
    card: Card,
    positionMillis: Long,
    durationMillis: Long?,
    parentIdentity: MediaIdentity? = card.parent_identity,
    ended: Boolean = false,
) {
    val profile = requireNotNull(input.profileId) { "Playback is not bound to a profile." }
    val duration = (durationMillis ?: 0).coerceAtLeast(0)
    val position = if (ended && duration > 0) duration else positionMillis.coerceAtLeast(0)
    val record = PlaybackRecord(presentation = card, identity = card.identity, provider_id = card.provider_id, title = card.title, poster = card.poster,
        landscape = card.landscape, position_millis = position, duration_millis = duration, updated_at_millis = 0, parent_identity = parentIdentity, metadata_origin = card.metadata_origin)
    call("/api/profiles/${profile.encodeURLPathPart()}/progress", HttpMethod.Put, buildJsonObject {
        put("record", Json.encodeToJsonElement(record))
        parentIdentity?.let { put("parent_identity", Json.encodeToJsonElement(it)) }
        put("ended", ended)
    })
}
