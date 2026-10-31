@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.viptv.app.components.LocalReducedMotion
import org.viptv.app.components.Motion
import org.viptv.app.components.motionSpec
import org.viptv.app.components.rememberEnterLeaveMotion
import org.viptv.app.components.rememberOverlayMotion
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

class OverlayMotionTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun render(scene: ImageComposeScene, n: Int, start: Int = 0): Int {
        var frame = start
        repeat(n) { scene.render((++frame) * 16_666_667L).close() }
        return frame
    }

    private fun containerAndScrim(reduced: Boolean, visible: Boolean, sink: (container: Float, scrim: Float) -> Unit) =
        ImageComposeScene(400, 700, Density(1f)) {
            ViptvTheme(false, pointer = true) {
                CompositionLocalProvider(LocalReducedMotion provides reduced) {
                    val motion = rememberOverlayMotion(visible,
                        containerEnter = motionSpec(tween(Motion.panel, easing = Motion.standard)),
                        scrimEnter = motionSpec(tween(Motion.arrive, easing = Motion.standard)),
                        leave = motionSpec(tween(Motion.leave, easing = Motion.exit)))
                    sink(motion.container, motion.scrim)
                }
            }
        }

    @Test fun reduceMotionPresentsTheOverlayInOneFrame() = runBlocking {
        var container = -1f
        var scrim = -1f
        val scene = containerAndScrim(reduced = true, visible = true) { c, s -> container = c; scrim = s }
        try {
            render(scene, 2)
            assertEquals(1f, container, .001f, "reduce motion opens the container in one frame")
            assertEquals(1f, scrim, .001f, "reduce motion opens the scrim in one frame")
        } finally { scene.close() }
    }

    @Test fun overlayMotionRampsTheContainerIn() = runBlocking {
        var container = -1f
        val scene = containerAndScrim(reduced = false, visible = true) { c, _ -> container = c }
        try {
            render(scene, 6)
            val partway = container
            assertTrue(partway > 0f && partway < 1f, "the container is on its way after a few frames, not $partway")
            render(scene, 30, 6)
            assertEquals(1f, container, .001f, "the container settles open")
        } finally { scene.close() }
    }

    @Test fun overlayMotionLeavesAndUnpresents() = runBlocking {
        val visible = mutableStateOf(true)
        var container = -1f
        var presented = true
        var frame = 0
        val scene = ImageComposeScene(400, 700, Density(1f)) {
            ViptvTheme(false, pointer = true) {
                val motion = rememberOverlayMotion(visible.value,
                    containerEnter = motionSpec(tween(Motion.panel, easing = Motion.standard)),
                    scrimEnter = motionSpec(tween(Motion.arrive, easing = Motion.standard)),
                    leave = motionSpec(tween(Motion.leave, easing = Motion.exit)))
                container = motion.container
                presented = motion.presented
            }
        }
        try {
            frame = render(scene, 30, frame)
            assertEquals(1f, container, .001f)
            visible.value = false
            frame = render(scene, 30, frame)
            assertEquals(0f, container, .001f, "the exit runs the container back to its edge")
            assertFalse(presented, "once the exit finishes the overlay is no longer presented")
        } finally { scene.close() }
    }

    @Test fun enterLeaveMotionSnapsUnderReduceMotion() = runBlocking {
        var progress = -1f
        val scene = ImageComposeScene(400, 700, Density(1f)) {
            ViptvTheme(false, pointer = true) {
                CompositionLocalProvider(LocalReducedMotion provides true) {
                    val motion = rememberEnterLeaveMotion(true,
                        enter = motionSpec(tween(Motion.arrive, easing = Motion.standard)),
                        leave = motionSpec(tween(Motion.leave, easing = Motion.exit)))
                    progress = motion.progress
                }
            }
        }
        try {
            render(scene, 2)
            assertEquals(1f, progress, .001f, "reduce motion presents the popover in one frame")
        } finally { scene.close() }
    }
}