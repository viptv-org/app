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
data class RankedSources(val rows: List<SourceCandidate>, val groups: SourceGroups = SourceGroups(), val bestMatchFullyMatches: Boolean = false,
    val reuseSupported: Boolean = false, val reuseIds: List<String> = emptyList(), val reuseReady: Boolean = false,
    val automaticIds: List<String>? = null, val reuseAudioLanguages: Map<String, String> = emptyMap(), val reusePreferred: Boolean = false, val discoveryPhase: String? = null)

internal fun SourceCandidate.failsHardFilter(): Boolean = release_type == "cam" || three_d ||
    (delivery == "torrent" && seeders?.let { it < 5 } == true)

// TODO(backend#21, core#12): drop once every ranking supplies `best_match_ids`.
internal fun interimSourceGroups(ranked: List<SourceCandidate>, bestMatchId: String? = ranked.firstOrNull()?.id): SourceGroups =
    SourceGroups.of(ranked.takeWhile { !it.failsHardFilter() }.take(SourceGroups.LIMIT).map { it.id }, bestMatchId)

suspend fun RemoteConnection.rankSources(profile: String, candidates: List<SourceCandidate>, context: RankingContext = RankingContext()): RankedSources {
    if (candidates.isEmpty()) return RankedSources(emptyList())
    if (candidates.size > SOURCE_RANKING_BATCH_SIZE) return rankSourcesBatched(candidates) { rankSources(profile, it, context) }
    suspend fun rank(memory: Boolean): JsonObject {
        val path = "/api/profiles/${profile.encodeURLPathPart()}/source-ranking"
        val rankedContext = context.copy(providerAffinity = context.providerAffinity.takeIf { providerAffinitySupported })
        val observations: Map<String, JsonElement> = if (memory) context.reuse.json(preferredSourceDiscoverySupported) else emptyMap()
        return try { call(path, HttpMethod.Post, JsonObject(sourceRankingRequest(candidates, rankedContext) + observations)).jsonObject }
        catch (error: RemoteFailure) {
            if (error.status != 400 || rankedContext.providerAffinity == null) throw error
            providerAffinitySupported = false
            call(path, HttpMethod.Post, JsonObject(sourceRankingRequest(candidates, context.copy(providerAffinity = null)) + observations)).jsonObject
        }
    }
    var response = rank(sourceReuseSupported && context.reuse.present)
    val supported = response["source_reuse_supported"]?.jsonPrimitive?.booleanOrNull == true
    if (supported && !sourceReuseSupported && context.reuse.present) response = rank(true)
    sourceReuseSupported = supported
    response["streams"]?.jsonArray.orEmpty().forEach { raw ->
        val row = raw.jsonObject
        reuseAudioLanguages[row.getValue("id").jsonPrimitive.content] = row["reuse_audio_language"]?.jsonPrimitive?.contentOrNull
    }
    return rankedSourcesResponse(if (preferredSourceDiscoverySupported) response else JsonObject(response - "source_reuse_ready"), candidates)
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
    val bestMatchFullyMatches = (response["best_match_fully_matches"] as? JsonPrimitive)?.booleanOrNull ?: false
    val rankedIds = ranked.mapTo(HashSet()) { it.id }
    val groups = if (supplied != null) SourceGroups.of(supplied.filter { it in rankedIds }, bestMatchId)
        else interimSourceGroups(ranked, bestMatchId)
    fun ids(key: String) = (response[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }?.filter { it in rankedIds }
    return RankedSources(ranked, groups, bestMatchFullyMatches,
        response["source_reuse_supported"]?.jsonPrimitive?.booleanOrNull == true, ids("source_reuse_ids").orEmpty(),
        response["source_reuse_ready"]?.jsonPrimitive?.booleanOrNull
            ?: (response["source_reuse_preferred"]?.jsonPrimitive?.booleanOrNull == true && response["source_reuse_fully_matches"]?.jsonPrimitive?.booleanOrNull == true),
        ids("automatic_source_ids"), response.getValue("streams").jsonArray.mapNotNull { raw ->
            val row = raw.jsonObject
            row["reuse_audio_language"]?.jsonPrimitive?.contentOrNull?.let { row.getValue("id").jsonPrimitive.content to it }
        }.toMap(), response["source_reuse_preferred"]?.jsonPrimitive?.booleanOrNull == true)
}

internal fun sourceRankingRequest(candidates: List<SourceCandidate>, context: RankingContext): JsonObject = buildJsonObject {
    put("ids", JsonArray(candidates.map { JsonPrimitive(it.id) }))
    wireHeight(context.device.maxHeight)?.let { put("device_max_height", it) }
    deviceCapabilityJson(context.device)?.let { put("device", it) }
    context.seriesMemory?.let(::seriesMemoryJson)?.let { put("series_memory", it) }
    affinityText(context.providerAffinity)?.let { put("provider_affinity", it) }
}

internal const val SOURCE_RANKING_BATCH_SIZE = 120

internal suspend fun rankSourcesBatched(candidates: List<SourceCandidate>, rank: suspend (List<SourceCandidate>) -> RankedSources): RankedSources {
    val pages = candidates.distinctBy { it.id }.chunked(SOURCE_RANKING_BATCH_SIZE).map { rank(it) }
    val shortlistIds = pages.flatMap { page -> page.groups.bestMatches + page.automaticIds.orEmpty().take(3) + page.reuseIds.take(3) }.toSet()
    val shortlist = pages.flatMap { it.rows }.filter { it.id in shortlistIds }.distinctBy { it.id }
    val decision = if (shortlist.isNotEmpty()) rank(shortlist) else pages.first()
    return decision.copy(rows = (decision.rows + pages.flatMap { it.rows }).distinctBy { it.id },
        reuseAudioLanguages = pages.flatMap { it.reuseAudioLanguages.entries }.associate { it.key to it.value } + decision.reuseAudioLanguages)
}
