package org.viptv.app.connection

import androidx.compose.runtime.Immutable
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.SourceCandidate

@Immutable
data class SourceGroups(val bestMatches: List<String> = emptyList(), val bestMatchId: String? = null) {
    companion object {
        const val LIMIT = 5

        fun of(bestMatches: List<String>, bestMatchId: String?) =
            bestMatches.distinct().take(LIMIT).let { ids -> SourceGroups(ids, bestMatchId?.takeIf { it in ids }) }
    }
}

@Immutable
data class RankedSources(val rows: List<SourceCandidate>, val groups: SourceGroups = SourceGroups())

internal fun SourceCandidate.failsHardFilter(): Boolean = release_type == "cam" || three_d ||
    (delivery == "torrent" && seeders?.let { it < 5 } == true)

// TODO(backend#21, core#12): drop once every ranking supplies `best_match_ids`.
internal fun interimSourceGroups(ranked: List<SourceCandidate>, bestMatchId: String? = ranked.firstOrNull()?.id): SourceGroups =
    SourceGroups.of(ranked.takeWhile { !it.failsHardFilter() }.take(SourceGroups.LIMIT).map { it.id }, bestMatchId)

suspend fun RemoteConnection.rankSources(profile: String, candidates: List<SourceCandidate>, deviceMaxHeight: Int? = null): RankedSources {
    if (candidates.isEmpty()) return RankedSources(emptyList())
    val response = call("/api/profiles/${profile.encodeURLPathPart()}/source-ranking", HttpMethod.Post,
        sourceRankingRequest(candidates, deviceMaxHeight)).jsonObject
    return rankedSourcesResponse(response, candidates)
}

internal fun rankedSourcesResponse(response: JsonObject, candidates: List<SourceCandidate>): RankedSources {
    val known = candidates.associateBy { it.id }
    val ranked = response.getValue("streams").jsonArray.map { item ->
        val row = item.jsonObject
        known[row.getValue("id").jsonPrimitive.content] ?: throw RemoteFailure(502, "The server returned an unknown source choice.")
    }
    if (ranked.map { it.id }.distinct().size != ranked.size) throw RemoteFailure(502, "The server repeated a source choice.")
    val bestMatchId = (response["best_match_id"] as? JsonPrimitive)?.contentOrNull
    val supplied = (response["best_match_ids"] as? JsonArray)?.map { it.jsonPrimitive.content }
    val rankedIds = ranked.mapTo(HashSet()) { it.id }
    val groups = if (supplied != null) SourceGroups.of(supplied.filter { it in rankedIds }, bestMatchId)
        else interimSourceGroups(ranked, bestMatchId)
    return RankedSources(ranked, groups)
}

internal fun sourceRankingRequest(candidates: List<SourceCandidate>, deviceMaxHeight: Int?): JsonObject = buildJsonObject {
    put("ids", JsonArray(candidates.map { JsonPrimitive(it.id) }))
    deviceMaxHeight?.takeIf { it in 144..4320 }?.let { put("device_max_height", it) }
}
