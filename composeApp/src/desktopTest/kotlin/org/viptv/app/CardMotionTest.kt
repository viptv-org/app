@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.InternalComposeUiApi::class)

package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import org.junit.Rule
import org.viptv.app.components.Button
import org.viptv.app.components.IconButton
import org.viptv.app.components.LocalReducedMotion
import org.viptv.app.components.Motion
import org.viptv.app.features.home.MEDIA_CARD_HOVER_RING_TAG
import org.viptv.app.features.home.MEDIA_CARD_HOVER_TAG
import org.viptv.app.features.home.MISSING_ART_TAG
import org.viptv.app.features.home.MediaCard
import org.viptv.app.features.player.PlaybackButtons
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.viptv.engine.wire.MetadataOrigin
import kotlin.math.abs
import kotlin.test.*

class CardMotionTest {
    @get:Rule val uiThread = UiThreadRule()

    private val origin = MetadataOrigin("stremio", "addon:4", "c".repeat(32))
    private val card = Card(MediaIdentity("tt0111161", "movie", emptyList()), provider_id = "addon:4",
        title = "Plain Movie", badges = emptyList(), metadata_origin = origin)

    private fun ui(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)

    private class Scene(pointer: Boolean = true, television: Boolean = false, reduced: Boolean = false,
        content: @Composable () -> Unit) : AutoCloseable {
        val scene = ImageComposeScene(900, 600, Density(1f)) {
            ViptvTheme(television, pointer = pointer) {
                CompositionLocalProvider(LocalReducedMotion provides reduced) {
                    Surface(Modifier.fillMaxSize(), color = Palette.background) { content() }
                }
            }
        }
        private var time = 0L
        private fun millis() = time / 1_000_000
        suspend fun frames(n: Int = 1) = repeat(n) { scene.render(time).close(); time += 16_666_667L; delay(2) }
        fun pixel(at: Offset): Int = scene.render(time).use { Bitmap.makeFromImage(it) }.getColor(at.x.toInt(), at.y.toInt())
        private fun nodes(merged: Boolean) = scene.semanticsOwners.flatMap { it.getAllSemanticsNodes(merged) }
        fun tagged(tag: String) = nodes(false).filter { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun art() = tagged(MISSING_ART_TAG).first().boundsInRoot
        fun press(at: Offset) = scene.sendPointerEvent(PointerEventType.Press, at, type = PointerType.Mouse,
            buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary, timeMillis = millis())
        fun move(at: Offset, pressed: Boolean = false) =
            if (pressed) scene.sendPointerEvent(PointerEventType.Move, at, type = PointerType.Mouse,
                buttons = PointerButtons(isPrimaryPressed = true), timeMillis = millis())
            else scene.sendPointerEvent(PointerEventType.Move, at)
        fun release(at: Offset) = scene.sendPointerEvent(PointerEventType.Release, at, type = PointerType.Mouse,
            button = PointerButton.Primary, timeMillis = millis())
        fun control(label: String): Rect = nodes(true).first {
            it.config.contains(SemanticsActions.OnClick) && spoken(it).contains(label)
        }.boundsInRoot
        private fun spoken(node: SemanticsNode) = (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).joinToString(" ")
        override fun close() = scene.close()
    }

    private fun assertScale(expected: Float, before: Rect, after: Rect, what: String, moved: Boolean = false) {
        val ratio = after.width / before.width
        assertTrue(abs(ratio - expected) < .006f, "$what: expected a scale of $expected, saw $ratio")
        if (!moved) assertTrue(abs(after.center.x - before.center.x) < 1f && abs(after.center.y - before.center.y) < 1f,
            "$what: the scale is centred on the surface")
    }

    private fun cardScene(television: Boolean = false, reduced: Boolean = false) =
        Scene(pointer = !television, television = television, reduced = reduced) {
            Box(Modifier.fillMaxSize().padding(40.dp)) { MediaCard(card, width = 240, open = {}) }
        }

    @Test fun aHoveredCardFadesItsScrimAndRingInAndOut() = ui {
        val scene = cardScene()
        try {
            scene.frames(2)
            val art = scene.art()
            val sample = Offset(art.left + 14f, art.top + 14f)
            val rest = scene.pixel(sample)
            assertEquals(Palette.raised.toArgb(), rest, "the art rests undimmed")
            assertTrue(scene.tagged(MEDIA_CARD_HOVER_TAG).isEmpty(), "no card is hovered at rest")

            scene.move(art.center)
            scene.frames(6)
            assertEquals(1, scene.tagged(MEDIA_CARD_HOVER_TAG).size, "hovering shows the scrim and the disc")
            assertEquals(1, scene.tagged(MEDIA_CARD_HOVER_RING_TAG).size, "hovering draws the inset ring")
            val starting = scene.pixel(sample)
            scene.frames(10)
            val settled = scene.pixel(sample)
            assertNotEquals(rest, starting, "the scrim starts fading as soon as the pointer arrives")
            assertNotEquals(settled, starting, "the scrim is still on its way a few frames in")

            scene.move(Offset(880f, 580f))
            scene.frames(4)
            assertEquals(1, scene.tagged(MEDIA_CARD_HOVER_TAG).size, "the scrim fades out instead of vanishing")
            scene.frames(12)
            assertTrue(scene.tagged(MEDIA_CARD_HOVER_TAG).isEmpty(), "the scrim clears once the fade ends")
            assertTrue(scene.tagged(MEDIA_CARD_HOVER_RING_TAG).isEmpty(), "the ring clears with the scrim")
            assertEquals(rest, scene.pixel(sample), "the art returns to its resting dim")
        } finally { scene.close() }
    }

    @Test fun reduceMotionAppliesTheCardHoverInOneFrame() = ui {
        val scene = cardScene(reduced = true)
        try {
            scene.frames(2)
            val art = scene.art()
            val sample = Offset(art.left + 14f, art.top + 14f)
            val rest = scene.pixel(sample)
            scene.move(art.center)
            scene.frames(6)
            val first = scene.pixel(sample)
            assertNotEquals(rest, first, "the scrim is applied without motion")
            scene.frames(10)
            assertEquals(first, scene.pixel(sample), "reduce motion leaves no fade behind")
        } finally { scene.close() }
    }

    @Test fun pressingACardScalesItsArtAndReturnsOnRelease() = ui {
        val scene = cardScene()
        try {
            scene.frames(2)
            val rest = scene.art()
            scene.press(rest.center)
            scene.frames(12)
            assertScale(Motion.card, rest, scene.art(), "a pressed card")
            scene.release(rest.center)
            scene.frames(16)
            assertScale(1f, rest, scene.art(), "a released card")
        } finally { scene.close() }
    }

    @Test fun reduceMotionNeverScalesAPressedCard() = ui {
        val scene = cardScene(reduced = true)
        try {
            scene.frames(2)
            val rest = scene.art()
            scene.press(rest.center)
            scene.frames(12)
            assertScale(1f, rest, scene.art(), "a pressed card under reduce motion")
        } finally { scene.close() }
    }

    @Test fun aPressThatBecomesADragReturnsTheScaleAndDoesNotOpen() = ui {
        var opened = 0
        val scene = Scene {
            LazyRow(Modifier.fillMaxWidth().height(260.dp), contentPadding = PaddingValues(20.dp)) {
                items(6) { MediaCard(card, width = 240, open = { opened++ }) }
            }
        }
        try {
            scene.frames(2)
            val rest = scene.art()
            scene.press(rest.center)
            scene.frames(6)
            assertScale(Motion.card, rest, scene.art(), "a card held in a shelf")
            repeat(10) { scene.move(rest.center + Offset(20f * (it + 1), 0f), pressed = true); scene.frames(1) }
            scene.frames(4)
            assertScale(1f, rest, scene.art(), "a press that turned into a drag", moved = true)
            scene.release(rest.center + Offset(200f, 0f))
            scene.frames(8)
            assertEquals(0, opened, "a drag over a card does not open it")
        } finally { scene.close() }
    }

    @Test fun pressingAButtonScalesItToTheButtonToken() = ui {
        val scene = Scene { Box(Modifier.fillMaxSize().padding(40.dp)) { Button(onClick = {}) { Text("Play now") } } }
        try {
            scene.frames(2)
            val rest = scene.control("Play now")
            scene.press(rest.center)
            scene.frames(12)
            assertScale(Motion.button, rest, scene.control("Play now"), "a pressed button")
        } finally { scene.close() }
    }

    @Test fun pressingAnIconButtonScalesItToTheButtonToken() = ui {
        val scene = Scene { Box(Modifier.fillMaxSize().padding(40.dp)) { IconButton(onClick = {}) { Text("Icon") } } }
        try {
            scene.frames(2)
            val rest = scene.control("Icon")
            scene.press(rest.center)
            scene.frames(12)
            assertScale(Motion.button, rest, scene.control("Icon"), "a pressed icon button")
        } finally { scene.close() }
    }

    @Test fun aPlayerTransportControlScalesToTheControlToken() = ui {
        val scene = Scene {
            Box(Modifier.fillMaxSize()) {
                PlaybackButtons(paused = true, toggle = {}, skip = {}, volume = 1f, setVolume = {}, speed = 1.0,
                    setSpeed = {}, close = {}, tracks = {})
            }
        }
        try {
            scene.frames(2)
            val rest = scene.control("Play")
            scene.press(rest.center)
            scene.frames(12)
            assertScale(Motion.control, rest, scene.control("Play"), "a pressed player transport control")
        } finally { scene.close() }
    }

    @Test fun tvNeverScalesAPressedCard() = ui {
        val scene = cardScene(television = true)
        try {
            scene.frames(2)
            val rest = scene.art()
            scene.press(rest.center)
            scene.frames(12)
            assertScale(1f, rest, scene.art(), "a pressed card on TV")
        } finally { scene.close() }
    }
}
