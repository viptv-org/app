package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.SourceCandidate

/**
 * [deviceMaxHeight] is this device's display tier (144..4320); the server caps
 * Auto quality at it. Omitted when unknown, so the server keeps the profile cap.
 */
suspend fun RemoteConnection.rankSources(profile: String, candidates: List<SourceCandidate>, deviceMaxHeight: Int? = null): List<SourceCandidate> {
    if (candidates.isEmpty()) return emptyList()
    val response = call("/api/profiles/${profile.encodeURLPathPart()}/source-ranking", HttpMethod.Post,
        sourceRankingRequest(candidates, deviceMaxHeight)).jsonObject
    val known = candidates.associateBy { it.id }
    val ranked = response.getValue("streams").jsonArray.map { item ->
        val row = item.jsonObject
        known[row.getValue("id").jsonPrimitive.content] ?: throw RemoteFailure(502, "The server returned an unknown source choice.")
    }
    if (ranked.map { it.id }.distinct().size != ranked.size) throw RemoteFailure(502, "The server repeated a source choice.")
    return ranked
}

internal fun sourceRankingRequest(candidates: List<SourceCandidate>, deviceMaxHeight: Int?): JsonObject = buildJsonObject {
    put("ids", JsonArray(candidates.map { JsonPrimitive(it.id) }))
    deviceMaxHeight?.takeIf { it in 144..4320 }?.let { put("device_max_height", it) }
}
