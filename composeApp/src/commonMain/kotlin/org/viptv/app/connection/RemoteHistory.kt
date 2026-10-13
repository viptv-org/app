package org.viptv.app.connection

import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.PlaybackRecord
import org.viptv.engine.wire.HistoryEntry

internal suspend fun RemoteConnection.history(profile: String): List<HistoryDisplay> {
    val codec = Json { ignoreUnknownKeys = true }
    val result = mutableListOf<HistoryDisplay>()
    var offset = 0
    do {
        val values = call("/api/v3/profiles/${profile.encodeURLPathPart()}/history?limit=100&offset=$offset")
        val page = codec.decodeFromJsonElement<List<HistoryEntry>>(values)
        result += page.map { HistoryDisplay(it, historyDayLabel(it.updated_at_millis)) }
        offset += page.size
    } while (page.size == 100)
    return result
}

internal suspend fun RemoteConnection.clearHistory(profile: String) {
    call("/api/v3/profiles/${profile.encodeURLPathPart()}/history", HttpMethod.Delete)
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
        landscape = card.landscape, position_millis = position, duration_millis = duration, updated_at_millis = 0, parent_identity = parentIdentity)
    call("/api/v3/profiles/${profile.encodeURLPathPart()}/progress", HttpMethod.Put, buildJsonObject {
        put("record", Json.encodeToJsonElement(record))
        parentIdentity?.let { put("parent_identity", Json.encodeToJsonElement(it)) }
        put("ended", ended)
    })
}
