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

    private class Timed(val scene: ImageComposeScene, val fired: () -> Boolean) : AutoCloseable {
        var frame = 0L
        fun render(times: Int = 1) = repeat(times) { scene.render(frame++ * 250_000_000L).close() }
        fun sceneMillis() = frame * 250_000_000L / 1_000_000
        override fun close() = scene.close()
    }

    private fun timer(millis: Long): Timed {
        var fired = false
        val scene = ImageComposeScene(100, 100, Density(1f)) {
            LaunchedEffect(Unit) { frameDelay(millis); fired = true }
        }
        return Timed(scene) { fired }
    }

    @Test fun aFrameDelayFinishesOnlyOnceTheSceneHasRunOutItsWindow() {
        val timer = timer(5_000)
        try {
            timer.render(19)
            assertFalse(timer.fired(), "at ${timer.sceneMillis()} ms of scene time the delay has not run out")
            timer.render(3)
            assertTrue(timer.sceneMillis() > 5_000, "the delay interval is counted in the frames the scene renders")
            assertTrue(timer.fired(), "a frame past the window finishes the delay")
        } finally { timer.close() }
    }

    @Test fun aFrameThatJumpsOverTheWindowFinishesTheDelayInOneStep() {
        val timer = timer(5_000)
        try {
            timer.render()
            assertFalse(timer.fired())
            timer.scene.render(timer.frame++ * 9_000_000_000L).close()
            assertTrue(timer.fired(), "a stalled window that comes back past the window finishes the delay")
        } finally { timer.close() }
    }
}