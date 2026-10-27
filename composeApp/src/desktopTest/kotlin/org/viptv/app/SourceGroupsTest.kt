package org.viptv.app

import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.features.sources.*
import org.viptv.engine.wire.SourceCandidate
import kotlin.test.*

/** SRC-BEST-MATCH-001 Source groups: membership from the ranking, headers over the visible rows. */
class SourceGroupsTest {
    private fun row(id: String, provider: String = "addon:one", releaseType: String? = null) =
        SourceCandidate(id, provider, "Source $id", release_type = releaseType)
    private fun headers(rows: List<SourceListRow>) = rows.mapNotNull { it.header }
    private fun marked(rows: List<SourceListRow>) = rows.filter { it.bestMatch }.map { it.source.id }

    @Test fun sevenPassingRowsAndTwoCameraReleasesFormBothGroups() {
        // Scenario 13.
        val ranked = (1..7).map { row("r$it") } + listOf(row("cam1", releaseType = "cam"), row("cam2", releaseType = "cam"))
        val groups = interimSourceGroups(ranked)
        assertEquals(listOf("r1", "r2", "r3", "r4", "r5"), groups.bestMatches)
        val rows = sourceListRows(ranked, groups)
        assertEquals(ranked.map { it.id }, rows.map { it.source.id })
        assertEquals(listOf(BEST_MATCHES_HEADER, ALL_SOURCES_HEADER), headers(rows))
        assertEquals(BEST_MATCHES_HEADER, rows[0].header)
        assertEquals(ALL_SOURCES_HEADER, rows[5].header, "The sixth row opens All sources")
        assertEquals(listOf("r1"), marked(rows))
    }

    @Test fun threePassingRowsShowOnlyTheBestMatchesHeader() {
        // Scenario 14.
        val ranked = (1..3).map { row("r$it") }
        val rows = sourceListRows(ranked, interimSourceGroups(ranked))
        assertEquals(listOf(BEST_MATCHES_HEADER), headers(rows))
    }

    @Test fun onlyCameraReleasesShowNoHeaderAndNoMarker() {
        // Scenario 15: the server still names the camera row as best_match_id today.
        val ranked = listOf(row("cam1", releaseType = "cam"), row("cam2", releaseType = "cam"))
        val groups = interimSourceGroups(ranked, bestMatchId = "cam1")
        assertEquals(SourceGroups(), groups)
        val rows = sourceListRows(ranked, groups)
        assertTrue(headers(rows).isEmpty()); assertTrue(marked(rows).isEmpty())
    }

    @Test fun aFilterHidingEveryBestMatchHidesBothHeadersAndTheMarker() {
        // Scenario 16: membership is computed before filters and survives them.
        val ranked = (1..5).map { row("r$it", "addon:one") } + listOf(row("x1", "addon:two"), row("x2", "addon:two"))
        val groups = interimSourceGroups(ranked)
        val filtered = sourceListRows(ranked.filter { it.provider_id == "addon:two" }, groups)
        assertEquals(listOf("x1", "x2"), filtered.map { it.source.id })
        assertTrue(headers(filtered).isEmpty()); assertTrue(marked(filtered).isEmpty())
        val cleared = sourceListRows(ranked, groups)
        assertEquals(listOf(BEST_MATCHES_HEADER, ALL_SOURCES_HEADER), headers(cleared))
        assertEquals(listOf("r1"), marked(cleared), "Clearing the filter restores the marker on the same row")
    }

    @Test fun aFilterHidingTheBestMatchRowLeavesNoMarkerButKeepsTheGroup() {
        val ranked = listOf(row("r1", "addon:two"), row("r2"), row("r3"))
        val rows = sourceListRows(ranked.filter { it.provider_id == "addon:one" }, interimSourceGroups(ranked))
        assertEquals(listOf(BEST_MATCHES_HEADER), headers(rows))
        assertTrue(marked(rows).isEmpty(), "The marker never moves to the next visible row")
    }

    @Test fun noRankingMeansOneListWithoutHeaders() {
        val rows = sourceListRows(listOf(row("a"), row("b")), SourceGroups())
        assertTrue(headers(rows).isEmpty()); assertTrue(marked(rows).isEmpty())
    }

    @Test fun hardFiltersFollowTheSharedEngine() {
        assertTrue(row("cam", releaseType = "cam").failsHardFilter())
        assertTrue(row("3d").copy(three_d = true).failsHardFilter())
        assertTrue(row("few").copy(delivery = "torrent", seeders = 4).failsHardFilter())
        assertFalse(row("five").copy(delivery = "torrent", seeders = 5).failsHardFilter())
        assertFalse(row("unknown").copy(delivery = "torrent").failsHardFilter(), "An unknown seeder count is not filtered")
        assertFalse(row("direct").copy(delivery = "direct", seeders = 0).failsHardFilter())
    }

    @Test fun suppliedMembershipWinsOverPosition() {
        val candidates = listOf(row("a"), row("b"), row("c"))
        val response = buildJsonObject {
            put("streams", buildJsonArray { listOf("a", "b", "c").forEach { add(buildJsonObject { put("id", it) }) } })
            put("best_match_id", "a")
            put("best_match_ids", buildJsonArray { add("a"); add("c"); add("gone") })
        }
        val ranked = rankedSourcesResponse(response, candidates)
        assertEquals(listOf("a", "b", "c"), ranked.rows.map { it.id })
        assertEquals(SourceGroups(listOf("a", "c"), "a"), ranked.groups, "Ids the ranking did not return are ignored")
    }

    @Test fun todaysResponseUsesTheInterimRule() {
        val candidates = listOf(row("a"), row("cam", releaseType = "cam"))
        val response = buildJsonObject {
            put("streams", buildJsonArray { add(buildJsonObject { put("id", "a") }); add(buildJsonObject { put("id", "cam") }) })
            put("best_match_id", "a")
        }
        assertEquals(SourceGroups(listOf("a"), "a"), rankedSourcesResponse(response, candidates).groups)
    }

    @Test fun membershipIsCappedAtFive() {
        assertEquals(5, SourceGroups.of((1..8).map { "r$it" }, "r1").bestMatches.size)
        assertNull(SourceGroups.of(listOf("a"), "z").bestMatchId)
    }
}
