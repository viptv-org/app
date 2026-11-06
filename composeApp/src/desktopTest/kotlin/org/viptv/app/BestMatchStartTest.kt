package org.viptv.app

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.viptv.app.connection.RankedSources
import org.viptv.app.connection.SourceGroups
import org.viptv.app.state.BestMatchStart
import org.viptv.engine.wire.SourceCandidate
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class BestMatchStartTest {
    private fun ranked(id: String, fullyMatches: Boolean = false): RankedSources {
        val row = SourceCandidate(id, "addon:$id", "Source $id", quality_height = 1080)
        return RankedSources(listOf(row), SourceGroups(listOf(id), id), fullyMatches)
    }

    // The decisions the start controller makes, each with the virtual time it made them at.
    private fun TestScope.starts(stopped: () -> Boolean = { false }): Pair<Channel<RankedSources>, List<Pair<Long, String?>>> {
        val rankings = Channel<RankedSources>(Channel.CONFLATED)
        val decisions = mutableListOf<Pair<Long, String?>>()
        val scheduler = testScheduler
        backgroundScope.launch {
            val target = BestMatchStart().await(rankings, stopped)
            decisions += scheduler.currentTime to target?.source?.id
        }
        runCurrent()
        return rankings to decisions
    }

    @Test fun aBetterRowInsideTheGraceWindowStartsAndDoesNotExtendIt() = runTest {
        val (rankings, decisions) = starts()
        rankings.send(ranked("first", fullyMatches = true))
        runCurrent()
        advanceTimeBy(1_000)
        rankings.send(ranked("better", fullyMatches = true))
        runCurrent()
        advanceTimeBy(400)
        assertTrue(decisions.isEmpty(), "The window stays open for the better row")
        advanceTimeBy(100); runCurrent()
        assertEquals(listOf(1_500L to "better"), decisions)
    }

    @Test fun aFullMatchStartsAfterTheGraceWindowWhenNothingBetterArrives() = runTest {
        val (rankings, decisions) = starts()
        rankings.send(ranked("only", fullyMatches = true))
        runCurrent()
        advanceTimeBy(1_499)
        assertTrue(decisions.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(1_500L to "only"), decisions)
    }

    @Test fun aFullMatchArrivingAfterTheCapIsArmedWaitsItsOwnWindow() = runTest {
        val (rankings, decisions) = starts()
        rankings.send(ranked("near"))
        runCurrent()
        advanceTimeBy(4_000)
        rankings.send(ranked("full", fullyMatches = true))
        runCurrent()
        advanceTimeBy(999)
        assertTrue(decisions.isEmpty(), "The grace window outlasts the cap that was armed before it")
        advanceTimeBy(501); runCurrent()
        assertEquals(listOf(5_500L to "full"), decisions)
    }

    @Test fun aBetterRankedRowThatMissesAPreferenceDoesNotReplaceTheFullMatch() = runTest {
        val (rankings, decisions) = starts()
        rankings.send(ranked("full", fullyMatches = true))
        runCurrent()
        advanceTimeBy(500)
        rankings.send(ranked("higher", fullyMatches = false))
        runCurrent()
        advanceTimeBy(999)
        assertTrue(decisions.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(1_500L to "full"), decisions)
    }

    @Test fun withNoFullMatchTheBestRowStartsAtTheCap() = runTest {
        val (rankings, decisions) = starts()
        rankings.send(ranked("first"))
        runCurrent()
        advanceTimeBy(2_000)
        rankings.send(ranked("later"))
        runCurrent()
        advanceTimeBy(2_999)
        assertTrue(decisions.isEmpty(), "The cap runs from the first eligible row, not from the latest one")
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(5_000L to "later"), decisions)
    }

    @Test fun discoveryEndingBeforeTheCapStartsTheBestRowThen() = runTest {
        val (rankings, decisions) = starts()
        rankings.send(ranked("first"))
        runCurrent()
        advanceTimeBy(1_200)
        rankings.send(ranked("later"))
        runCurrent()
        advanceTimeBy(300)
        rankings.close()
        runCurrent()
        assertEquals(listOf(1_500L to "later"), decisions)
    }

    @Test fun discoveryEndingStartsAFullMatchWithoutWaitingOutTheWindow() = runTest {
        val (rankings, decisions) = starts()
        rankings.send(ranked("full", fullyMatches = true))
        runCurrent()
        advanceTimeBy(400)
        rankings.close()
        runCurrent()
        assertEquals(listOf(400L to "full"), decisions)
    }

    @Test fun noEligibleRowStartsNothing() = runTest {
        val (rankings, decisions) = starts()
        rankings.send(RankedSources(emptyList()))
        runCurrent()
        advanceTimeBy(6_000)
        rankings.close()
        runCurrent()
        assertEquals(listOf(6_000L to null), decisions)
    }

    @Test fun aStopWhileTheWindowRunsStartsNothing() = runTest {
        var stopped = false
        val (rankings, decisions) = starts { stopped }
        rankings.send(ranked("full", fullyMatches = true))
        runCurrent()
        advanceTimeBy(1_000)
        stopped = true
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(1_500L to null), decisions)
    }

    @Test fun aStopWithRowsStillArrivingEndsTheStartAtOnce() = runTest {
        var stopped = false
        val (rankings, decisions) = starts { stopped }
        rankings.send(ranked("first"))
        runCurrent()
        advanceTimeBy(500)
        stopped = true
        rankings.send(ranked("later"))
        runCurrent()
        assertEquals(listOf(500L to null), decisions)
    }
}