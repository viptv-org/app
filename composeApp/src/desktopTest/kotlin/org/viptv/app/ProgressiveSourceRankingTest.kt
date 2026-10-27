package org.viptv.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.viptv.app.connection.RankedSources
import org.viptv.app.connection.SourceGroups
import org.viptv.app.state.ProgressiveSourceRanking
import org.viptv.engine.wire.SourceCandidate
import kotlin.test.*

/** SRC-BEST-MATCH-001 Progressive ranking: rows are ranked and grouped as they arrive. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressiveSourceRankingTest {
    private fun row(id: String, score: Int) = SourceCandidate(id, "addon:$id", "Source $id", quality_height = score.toLong())

    /** A fake shared ranking: highest score first, the top two form Best matches. */
    private class FakeRanking(private val latency: Long = 0) {
        val calls = mutableListOf<Pair<Long, List<String>>>()
        var fail: (Int) -> Boolean = { false }
        suspend fun rank(scope: TestScope, rows: List<SourceCandidate>): RankedSources {
            calls += scope.currentTime to rows.map { it.id }
            delay(latency)
            if (fail(calls.size)) error("ranking unavailable")
            val ranked = rows.sortedByDescending { it.quality_height }
            return RankedSources(ranked, SourceGroups.of(ranked.take(2).map { it.id }, ranked.firstOrNull()?.id))
        }
    }

    private fun TestScope.ranking(fake: FakeRanking, shown: MutableList<RankedSources>) =
        ProgressiveSourceRanking(backgroundScope, rank = { fake.rank(this, it) }, show = { shown += it })

    @Test fun firstArrivalIsRankedAtOnceAndNeverShownInArrivalOrder() = runTest {
        val fake = FakeRanking(latency = 40); val shown = mutableListOf<RankedSources>()
        val ranking = ranking(fake, shown)
        ranking.arrived(listOf(row("a", 1), row("b", 3), row("c", 2)))
        runCurrent()
        assertEquals(listOf(0L to listOf("a", "b", "c")), fake.calls, "The first provider response is ranked without waiting")
        assertTrue(shown.isEmpty(), "Rows wait for their ranked positions; arrival order is never shown")
        advanceTimeBy(41)
        assertEquals(listOf("b", "c", "a"), shown.single().rows.map { it.id })
        assertEquals(SourceGroups(listOf("b", "c"), "b"), shown.single().groups)
    }

    @Test fun laterArrivalsMergeAtTheirRankedPositionsAndMoveRowsBetweenGroups() = runTest {
        val fake = FakeRanking(); val shown = mutableListOf<RankedSources>()
        val ranking = ranking(fake, shown)
        ranking.arrived(listOf(row("a", 1), row("b", 2)))
        runCurrent()
        assertEquals(SourceGroups(listOf("b", "a"), "b"), shown.last().groups)
        advanceTimeBy(500)
        ranking.arrived(listOf(row("a", 1), row("b", 2), row("best", 9)))
        runCurrent()
        assertEquals(listOf("best", "b", "a"), shown.last().rows.map { it.id }, "One update merges the new row in ranked order")
        assertEquals(SourceGroups(listOf("best", "b"), "best"), shown.last().groups, "The better row takes the marker and pushes a row out of the group")
        assertEquals(listOf("best", "b", "a"), ranking.finish()?.rows?.map { it.id })
    }

    @Test fun arrivalsInsideTheSpacingWindowCollapseToOneRequestForTheLatestRows() = runTest {
        val fake = FakeRanking(latency = 10); val shown = mutableListOf<RankedSources>()
        val ranking = ranking(fake, shown)
        ranking.arrived(listOf(row("a", 1)))
        runCurrent()
        advanceTimeBy(50); ranking.arrived(listOf(row("a", 1), row("b", 2)))
        advanceTimeBy(50); ranking.arrived(listOf(row("a", 1), row("b", 2), row("c", 3)))
        advanceTimeBy(50); ranking.arrived(listOf(row("a", 1), row("b", 2), row("c", 3), row("d", 4)))
        advanceTimeBy(5_000) // The worker lives in backgroundScope, which advanceUntilIdle ignores.
        assertEquals(listOf(0L to listOf("a"), 300L to listOf("a", "b", "c", "d")), fake.calls,
            "At most one ranking request per 300 ms, and only the latest rows are ranked")
        assertEquals(listOf("d", "c", "b", "a"), shown.last().rows.map { it.id })
    }

    @Test fun onlyOneRequestIsInFlightAndTheLatestRowsWin() = runTest {
        val fake = FakeRanking(latency = 1_000); val shown = mutableListOf<RankedSources>()
        val ranking = ranking(fake, shown)
        ranking.arrived(listOf(row("a", 1)))
        runCurrent()
        advanceTimeBy(400); ranking.arrived(listOf(row("a", 1), row("b", 2)))
        advanceTimeBy(400); ranking.arrived(listOf(row("a", 1), row("b", 2), row("c", 3)))
        advanceTimeBy(5_000) // The worker lives in backgroundScope, which advanceUntilIdle ignores.
        assertEquals(listOf(0L to listOf("a"), 1_000L to listOf("a", "b", "c")), fake.calls,
            "The next request waits for the one in flight and skips the superseded rows")
        assertEquals(listOf(listOf("a"), listOf("c", "b", "a")), shown.map { result -> result.rows.map { it.id } })
    }

    @Test fun finishRanksTheLastArrivalsBeforeReturning() = runTest {
        val fake = FakeRanking(); val shown = mutableListOf<RankedSources>()
        val ranking = ranking(fake, shown)
        ranking.arrived(listOf(row("a", 1)))
        runCurrent()
        ranking.arrived(listOf(row("a", 1), row("b", 2)))
        val final = ranking.finish()
        assertEquals(listOf("b", "a"), final?.rows?.map { it.id })
        assertEquals(final, shown.last())
        assertEquals(2, fake.calls.size)
    }

    @Test fun repeatedRowsAreNotRankedAgain() = runTest {
        val fake = FakeRanking(); val shown = mutableListOf<RankedSources>()
        val ranking = ranking(fake, shown)
        ranking.arrived(listOf(row("a", 1), row("b", 2)))
        runCurrent()
        ranking.arrived(listOf(row("a", 1), row("b", 2)))
        assertEquals(listOf("b", "a"), ranking.finish()?.rows?.map { it.id })
        assertEquals(1, fake.calls.size, "The final list equal to the last arrival needs no second request")
    }

    @Test fun nothingArrivedFinishesEmpty() = runTest {
        val fake = FakeRanking()
        val ranking = ranking(fake, mutableListOf())
        assertEquals(RankedSources(emptyList()), ranking.finish())
        assertTrue(fake.calls.isEmpty())
    }

    @Test fun aFailedRankingKeepsTheShownOrderAndAppendsLateArrivalsWithoutGroups() = runTest {
        val fake = FakeRanking().apply { fail = { it == 2 } }; val shown = mutableListOf<RankedSources>()
        val ranking = ranking(fake, shown)
        ranking.arrived(listOf(row("a", 1), row("b", 2)))
        runCurrent()
        assertEquals(listOf("b", "a"), shown.last().rows.map { it.id })
        advanceTimeBy(500)
        ranking.arrived(listOf(row("a", 1), row("b", 2), row("c", 5)))
        runCurrent()
        assertEquals(listOf("b", "a", "c"), shown.last().rows.map { it.id }, "Rows stay where they were; the late row appends")
        assertEquals(SourceGroups(), shown.last().groups, "No headers or marker after a failed ranking")
        advanceTimeBy(500)
        ranking.arrived(listOf(row("a", 1), row("b", 2), row("c", 5), row("d", 9)))
        assertNull(ranking.finish(), "No Best match autoplay after a failed ranking")
        assertEquals(listOf("b", "a", "c", "d"), shown.last().rows.map { it.id })
        assertEquals(2, fake.calls.size, "This discovery does not ask the ranking again")
    }

    @Test fun aFailedFirstRankingShowsArrivalOrder() = runTest {
        val fake = FakeRanking().apply { fail = { true } }; val shown = mutableListOf<RankedSources>()
        val ranking = ranking(fake, shown)
        ranking.arrived(listOf(row("a", 1), row("b", 2)))
        runCurrent()
        assertEquals(RankedSources(listOf(row("a", 1), row("b", 2))), shown.last())
        assertNull(ranking.finish())
    }
}
