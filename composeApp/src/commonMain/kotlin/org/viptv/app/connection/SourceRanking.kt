package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.SourceCandidate

suspend fun RemoteConnection.rankSources(profile: String, candidates: List<SourceCandidate>): List<SourceCandidate> {
    if (candidates.isEmpty()) return emptyList()
    val response = call("/api/v3/profiles/${profile.encodeURLPathPart()}/source-ranking", HttpMethod.Post,
        buildJsonObject { put("ids", JsonArray(candidates.map { JsonPrimitive(it.id) })) }).jsonObject
    val known = candidates.associateBy { it.id }
    val ranked = response.getValue("streams").jsonArray.map { item ->
        val row = item.jsonObject
        known[row.getValue("id").jsonPrimitive.content] ?: throw RemoteFailure(502, "The server returned an unknown source choice.")
    }
    if (ranked.map { it.id }.distinct().size != ranked.size) throw RemoteFailure(502, "The server repeated a source choice.")
    return ranked
}
