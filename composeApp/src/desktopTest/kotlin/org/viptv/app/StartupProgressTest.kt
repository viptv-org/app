package org.viptv.app

import org.viptv.app.state.StartupSignals
import org.viptv.app.state.StartupStage
import org.viptv.app.state.startupProgress
import kotlin.test.*

class StartupProgressTest {
    @Test fun noStartInFlightDrawsNothing() {
        assertNull(startupProgress(null, StartupSignals()))
        assertNull(startupProgress(null, StartupSignals(discovering = true)).let { startupProgress(it, StartupSignals()) })
    }

    @Test fun eachStageHoldsAtItsOwnFloorUntilTheNextOneArrives() {
        val discovery = assertNotNull(startupProgress(null, StartupSignals(discovering = true)))
        assertEquals(StartupStage.Discovery, discovery.stage)
        assertEquals(0f, discovery.fill)
        assertTrue(discovery.holding)

        val opening = assertNotNull(startupProgress(discovery, StartupSignals(opening = true)))
        assertEquals(StartupStage.Opening, opening.stage)
        assertTrue(opening.fill > discovery.fill)
        assertTrue(opening.holding)

        val buffering = assertNotNull(startupProgress(opening, StartupSignals(buffering = true)))
        assertEquals(StartupStage.Buffering, buffering.stage)
        assertTrue(buffering.fill > opening.fill)
        assertTrue(buffering.holding, "a stage without a measured share holds")
    }

    @Test fun theBufferedShareMovesTheFillAndNeverBackwards() {
        val start = assertNotNull(startupProgress(null, StartupSignals(buffering = true)))
        val quarter = assertNotNull(startupProgress(start, StartupSignals(buffering = true, bufferedFraction = 0.25f)))
        val all = assertNotNull(startupProgress(quarter, StartupSignals(buffering = true, bufferedFraction = 1f)))
        assertTrue(quarter.fill > start.fill)
        assertTrue(all.fill > quarter.fill)
        assertEquals(1f, all.fill)
        assertFalse(all.holding)

        val regressed = assertNotNull(startupProgress(all, StartupSignals(buffering = true, bufferedFraction = 0.1f)))
        assertEquals(all.fill, regressed.fill, "a renderer that refills the cache never pulls the art back down")
    }

    @Test fun anEarlierStageNeverPullsTheArtBackDown() {
        val buffering = assertNotNull(startupProgress(null, StartupSignals(buffering = true, bufferedFraction = 1f)))
        val reopened = assertNotNull(startupProgress(buffering, StartupSignals(opening = true)))
        assertEquals(StartupStage.Buffering, reopened.stage)
        assertEquals(1f, reopened.fill)
    }

    @Test fun theLineUnderTheArtIsTheStagesOwn() {
        assertEquals("Finding sources", assertNotNull(startupProgress(null, StartupSignals(discovering = true))).status)
        assertEquals("Still checking sources", assertNotNull(startupProgress(null, StartupSignals(discovering = true, sourcesArrived = true))).status)
        assertEquals("Opening Torrentio 1080p…", assertNotNull(startupProgress(null, StartupSignals(opening = true, openingStage = "Opening Torrentio 1080p…"))).status)
        assertEquals("Opening stream…", assertNotNull(startupProgress(null, StartupSignals(opening = true))).status)
        assertEquals("Buffering…", assertNotNull(startupProgress(null, StartupSignals(buffering = true))).status)
    }

    @Test fun aMeasuredShareIsClampedToTheStage() {
        val negative = assertNotNull(startupProgress(null, StartupSignals(buffering = true, bufferedFraction = -2f)))
        val over = assertNotNull(startupProgress(null, StartupSignals(buffering = true, bufferedFraction = 8f)))
        assertEquals(0.66f, (negative.fill * 100).toInt() / 100f)
        assertEquals(1f, over.fill)
    }
}