package org.viptv.app

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.viptv.app.features.home.HeroProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HeroProgressTest {
    private class Clock(val scope: TestScope) : MonotonicFrameClock {
        var frames = 0
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            frames++
            return onFrame(scope.testScheduler.currentTime * 1_000_000)
        }
    }

    @Test fun slideTakesEightSecondsAndRedrawsOncePerPixel() = runTest {
        val progress = HeroProgress().apply { steps = 48 }
        val clock = Clock(this)
        val start = testScheduler.currentTime
        withContext(clock) { progress.run("a") }
        assertEquals(8000, testScheduler.currentTime - start)
        assertEquals(1f, progress.fraction)
        assertTrue(clock.frames <= 49, "one wake-up per pixel step, not per vsync: ${clock.frames}")
    }

    @Test fun fillOnlyChangesInWholePixelSteps() = runTest {
        val progress = HeroProgress().apply { steps = 48 }
        val seen = mutableListOf<Float>()
        val runner = launch(Clock(this)) { progress.run("a") }
        repeat(8000 / 10) { advanceTimeBy(10); runCurrent(); seen += progress.fraction }
        runner.join()
        assertTrue(seen.all { kotlin.math.abs(it * 48 - kotlin.math.round(it * 48)) < 1e-3f }, "fill snaps to pixels: $seen")
        assertTrue(seen.zipWithNext().all { (a, b) -> b >= a }, "fill never moves backwards")
        assertEquals(49, (seen + progress.fraction).distinct().size)
        assertEquals(1f, progress.fraction)
    }

    @Test fun pausedSlideResumesWhereItStoppedAndANewSlideRestarts() = runTest {
        val progress = HeroProgress().apply { steps = 48 }
        val clock = Clock(this)
        val first = launch(clock) { progress.run("a") }
        advanceTimeBy(3000); runCurrent()
        first.cancel()
        val paused = progress.fraction
        assertTrue(paused in 0.35f..0.375f, "paused near 3 s: $paused")
        advanceTimeBy(10_000)
        assertEquals(paused, progress.fraction)
        val resumedAt = testScheduler.currentTime
        withContext(clock) { progress.run("a") }
        val rest = testScheduler.currentTime - resumedAt
        assertTrue(rest in 5000L..(5000L + 8000 / 48 + 1), "remaining time resumes: $rest")
        progress.bind("b")
        assertEquals(0f, progress.fraction)
    }
}
