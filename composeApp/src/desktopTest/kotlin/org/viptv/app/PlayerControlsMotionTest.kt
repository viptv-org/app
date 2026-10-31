@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package org.viptv.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.components.LocalReducedMotion
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.ViptvTheme
import kotlin.test.*

class PlayerControlsMotionTest {
    @get:Rule val uiThread = UiThreadRule()

    private fun ui(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private class Scene(reduced: Boolean = false) : AutoCloseable {
        val scene = ImageComposeScene(1280, 720, Density(1f)) {
            ViptvTheme(television = false, pointer = true) {
                CompositionLocalProvider(LocalReducedMotion provides reduced) {
                    PlayerScreen(null, false, null, {}, {}, {}, preview = PlayerFact.Progress(0, 20_000),
                        canvasColor = Color.Red)
                }
            }
        }
        private var time = 0L
        suspend fun frames(n: Int = 1) = repeat(n) { scene.render(time).close(); time += 16_666_667L; delay(2) }
        private fun red(at: Offset): Int = scene.render(time).use { Bitmap.makeFromImage(it) }
            .getColor(at.x.toInt(), at.y.toInt()) ushr 16 and 0xFF
        fun sample() = red(Offset(8f, 8f))
        fun tap(at: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, at)
            scene.sendPointerEvent(PointerEventType.Release, at)
        }
        override fun close() = scene.close()
    }

    @Test fun theControlLayerFadesOutAndBackInAroundATap() = ui {
        val scene = Scene()
        try {
            scene.frames(3)
            val shown = scene.sample()
            assertTrue(shown < 200, "the header scrim dims the canvas while the controls are shown, saw $shown")

            scene.tap(Offset(640f, 360f))
            scene.frames(6)
            val leaving = scene.sample()
            assertTrue(leaving > shown && leaving < 255, "the controls are fading out, saw $leaving")
            scene.frames(20)
            assertEquals(255f, scene.sample().toFloat(), 2f, "the controls have fully faded out to the canvas")

            scene.tap(Offset(640f, 360f))
            scene.frames(6)
            val arriving = scene.sample()
            assertTrue(arriving < 255 && arriving > shown, "the controls are fading back in, saw $arriving")
            scene.frames(20)
            assertEquals(shown.toFloat(), scene.sample().toFloat(), 2f, "the controls settle back to their shown dim")
        } finally { scene.close() }
    }

    @Test fun reduceMotionShowsAndHidesTheControlsInOneFrame() = ui {
        val scene = Scene(reduced = true)
        try {
            scene.frames(2)
            assertTrue(scene.sample() < 200, "the controls are shown under reduce motion")

            scene.tap(Offset(640f, 360f))
            scene.frames(2)
            assertEquals(255f, scene.sample().toFloat(), 2f, "reduce motion hides the controls in one frame")

            scene.tap(Offset(640f, 360f))
            scene.frames(2)
            assertTrue(scene.sample() < 200, "reduce motion shows the controls in one frame")
        } finally { scene.close() }
    }
}