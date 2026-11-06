@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.viptv.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.viptv.app.components.frameDelay
import kotlin.test.*

class FrameDelayTest {
    @get:Rule val uiThread = UiThreadRule()
    @get:Rule val deterministicImages = DeterministicImageLoaderRule()

    private class Timer(millis: Long) : AutoCloseable {
        var fired = false
            private set
        private var frame = 0L
        val scene = ImageComposeScene(100, 100, Density(1f)) {
            LaunchedEffect(Unit) { frameDelay(millis); fired = true }
        }

        fun render(times: Int = 1) = repeat(times) { scene.render(frame++ * StepNanos).close() }
        fun sceneMillis() = frame * StepNanos / 1_000_000
        fun jumpTo(nanos: Long) = scene.render(frame++ * nanos).close()
        override fun close() = scene.close()
    }

    @Test fun aFrameDelayFinishesOnlyOnceTheSceneHasRunOutItsWindow() {
        val timer = Timer(5_000)
        try {
            timer.render(19)
            assertFalse(timer.fired, "at ${timer.sceneMillis()} ms of scene time the delay has not run out")
            timer.render(3)
            assertTrue(timer.sceneMillis() > 5_000, "the delay interval is counted in the frames the scene renders")
            assertTrue(timer.fired, "a frame past the window finishes the delay")
        } finally { timer.close() }
    }

    @Test fun aFrameThatJumpsOverTheWindowFinishesTheDelayInOneStep() {
        val timer = Timer(5_000)
        try {
            timer.render()
            assertFalse(timer.fired)
            timer.jumpTo(9_000_000_000L)
            assertTrue(timer.fired, "a stalled window that comes back past the window finishes the delay")
        } finally { timer.close() }
    }
}

private const val StepNanos = 250_000_000L
