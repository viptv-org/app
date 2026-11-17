package org.viptv.app

import kotlin.test.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.SourceCandidate

class SourceReuseResponseTest {
    @Test fun readyReuseDoesNotRequirePerfectSoftPreferencesAndUnknownIdsAreIgnored() {
        val rows = listOf(SourceCandidate("eligible", "provider", "Source"))
        val response = Json.parseToJsonElement("""{"streams":[{"id":"eligible"}],"source_reuse_supported":true,"source_reuse_ids":["expired","eligible"],"source_reuse_ready":true,"best_match_fully_matches":false,"automatic_source_ids":["eligible"]}""").jsonObject
        val ranked = rankedSourcesResponse(response, rows)
        assertTrue(ranked.reuseReady)
        assertFalse(ranked.bestMatchFullyMatches)
        assertEquals(listOf("eligible"), ranked.reuseIds)
        assertEquals(listOf("eligible"), ranked.automaticIds)
    }

    @Test fun absentCapabilityNeverClaimsReuseSupport() {
        val rows = listOf(SourceCandidate("a", "provider", "Source"))
        val ranked = rankedSourcesResponse(Json.parseToJsonElement("""{"streams":[{"id":"a"}]}""").jsonObject, rows)
        assertFalse(ranked.reuseSupported)
        assertFalse(ranked.reuseReady)
    }
    @Test fun largeDiscoveriesAskCoreToRankTheShortlistWithinItsRequestLimit() = kotlinx.coroutines.runBlocking {
        val calls = mutableListOf<Int>()
        val rows = (1..300).map { SourceCandidate(it.toString(), "provider", "Source $it") }
        val result = rankSourcesBatched(rows) { batch ->
            calls += batch.size
            val sorted = batch.sortedByDescending { it.id.toInt() }
            RankedSources(sorted, SourceGroups.of(sorted.take(5).map { it.id }, sorted.first().id),
                reuseSupported = true, reuseIds = sorted.take(3).map { it.id }, reuseReady = true,
                automaticIds = sorted.map { it.id })
        }
        assertTrue(calls.all { it <= SOURCE_RANKING_BATCH_SIZE })
        assertEquals("300", result.groups.bestMatchId)
        assertEquals("300", result.reuseIds.first())
        assertEquals(300, result.rows.size)
    }

}
