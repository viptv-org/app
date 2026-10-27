package org.viptv.app.connection

import androidx.compose.runtime.Immutable
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.viptv.engine.wire.SourceCandidate

/**
 * SRC-BEST-MATCH-001 group membership that the shared ranking supplied: the ids of
 * the **Best matches** group, in ranking order, and the row carrying the Best match
 * marker. Membership covers every row of the discovery, before the provider and
 * quality filters. Clients render it and never work it out from row position.
 */
@Immutable
data class SourceGroups(val bestMatches: List<String> = emptyList(), val bestMatchId: String? = null) {
    companion object {
        /** At most this many rows form Best matches (SRC-BEST-MATCH-001, fixed). */
        const val LIMIT = 5

        /** Build membership; the marker only stays on a row that is in the group. */
        fun of(bestMatches: List<String>, bestMatchId: String?) =
            bestMatches.distinct().take(LIMIT).let { ids -> SourceGroups(ids, bestMatchId?.takeIf { it in ids }) }
    }
}

/** The ranking's answer for one set of rows: ranked order plus group membership. */
@Immutable
data class RankedSources(val rows: List<SourceCandidate>, val groups: SourceGroups = SourceGroups())

/**
 * A row that may never autoplay or join Best matches (SRC-BEST-MATCH-001 hard filters),
 * read from the same normalized facts the shared engine ranks by. Decode class is not
 * reported to the app yet, so *unsupported* rows cannot be recognized here.
 */
internal fun SourceCandidate.failsHardFilter(): Boolean = release_type == "cam" || three_d ||
    (delivery == "torrent" && seeders?.let { it < 5 } == true)

/**
 * Interim membership for a ranking that returns only its order (backend#21, core#12):
 * the leading rows that pass the hard filters, at most [SourceGroups.LIMIT]. The shared
 * ranking sorts every hard-filtered row last, so this is the group it describes. Remove
 * once every ranking supplies `best_match_ids`.
 */
internal fun interimSourceGroups(ranked: List<SourceCandidate>, bestMatchId: String? = ranked.firstOrNull()?.id): SourceGroups =
    SourceGroups.of(ranked.takeWhile { !it.failsHardFilter() }.take(SourceGroups.LIMIT).map { it.id }, bestMatchId)

/**
 * [deviceMaxHeight] is this device's display tier (144..4320); the server caps
 * Auto quality at it. Omitted when unknown, so the server keeps the profile cap.
 */
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
