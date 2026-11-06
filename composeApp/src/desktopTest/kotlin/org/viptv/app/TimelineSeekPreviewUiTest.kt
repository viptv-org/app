package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.viptv.app.features.player.PlaybackTimeline
import org.viptv.app.features.player.playbackTime
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme
import org.junit.Rule
import kotlin.math.abs
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class TimelineSeekPreviewUiTest {
    @get:Rule val uiThread = UiThreadRule()
    private val position = 842_000L
    private val duration = 3_132_000L
    private var frame = 0L

    private fun scene(width: Int, height: Int, remaining: Boolean, seek: (Long) -> Unit = {}) =
        ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
            Box(Modifier.fillMaxSize().background(Palette.background)) {
                Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { PlaybackTimeline(position, duration, seek, remaining = remaining) }
            }
        } }

    @Test fun draggingReadsTheTargetInBothLabelsAndABubbleAboveTheBar() {
        for ((width, height, remaining) in listOf(Triple(1280, 900, false), Triple(390, 844, true))) {
            val seeks = mutableListOf<Long>()
            val scene = scene(width, height, remaining) { seeks += it }
            try {
                frames(scene)
                val bar = slider(scene).boundsInRoot
                assertTrue(shows(scene, "14:02"), "$width×$height: the current label starts on playback")
                press(scene, bar.left + bar.width * 0.25f)
                drag(scene, bar.left + bar.width * 0.75f)
                frames(scene)
                val dragged = labels(scene).firstOrNull { it != "14:02" && it != playbackTime(duration) }
                val shown = assertNotNull(dragged, "$width×$height: no label follows the drag")
                assertAround(millis(shown).toDouble(), duration * 0.75, duration * 0.05, "$width×$height: the current label reads the target")
                val bubble = assertNotNull(bubble(scene, bar), "$width×$height: no preview bubble above the bar")
                assertEquals(shown, texts(bubble).first(), "$width×$height: the bubble reads the same target as the label")
                assertTrue(bubble.boundsInRoot.bottom <= bar.top, "$width×$height: the bubble sits above the bar: ${bubble.boundsInRoot} vs $bar")
                assertTrue(bubble.boundsInRoot.left >= 0 && bubble.boundsInRoot.right <= width, "$width×$height: the bubble leaves the player")
                val right = labels(scene).last()
                if (remaining) assertAround(millis(right.removePrefix("-")).toDouble(), (duration - millis(shown)).toDouble(), duration * 0.05,
                    "$width×$height: the remaining label counts down from the target")
                else assertEquals(playbackTime(duration), right, "$width×$height: the duration label does not move")
                release(scene, bar.left + bar.width * 0.75f)
                frames(scene)
                assertAround(millis(shown).toDouble(), seeks.lastOrNull()?.toDouble() ?: Double.NaN, duration * 0.01,
                    "$width×$height: release commits the dragged target")
                assertEquals("14:02", labels(scene).first(), "$width×$height: the labels read playback again after release")
                scene.sendPointerEvent(PointerEventType.Exit, Offset(bar.left + bar.width * 0.75f, bar.center.y))
                frames(scene)
                assertNull(bubble(scene, bar), "$width×$height: the bubble hides when the pointer leaves")
            } finally { scene.close() }
        }
    }

    @Test fun theBubbleStaysInsideThePlayerAtEitherEndOfTheBar() {
        for (fraction in listOf(0.01f, 0.99f)) {
            val scene = scene(1280, 900, remaining = false)
            try {
                frames(scene)
                val bar = slider(scene).boundsInRoot
                press(scene, bar.center.x)
                drag(scene, bar.left + bar.width * fraction)
                frames(scene)
                val bubble = assertNotNull(bubble(scene, bar), "no bubble at $fraction")
                assertTrue(bubble.boundsInRoot.left >= 0 && bubble.boundsInRoot.right <= 1280, "the bubble leaves the player at $fraction: ${bubble.boundsInRoot}")
            } finally { scene.close() }
        }
    }

    @Test fun hoveringTheBarShowsTheBubbleWithoutMovingTheLabels() {
        val scene = scene(1280, 900, remaining = false)
        try {
            frames(scene)
            val bar = slider(scene).boundsInRoot
            scene.sendPointerEvent(PointerEventType.Move, Offset(bar.left + bar.width * 0.5f, bar.center.y))
            frames(scene)
            val bubble = assertNotNull(bubble(scene, bar), "hovering shows the bubble")
            assertAround(millis(texts(bubble).first()).toDouble(), duration * 0.5, duration * 0.05, "the bubble reads the hovered time")
            assertEquals("14:02", labels(scene).first(), "hovering leaves the labels on playback")
            scene.sendPointerEvent(PointerEventType.Exit, Offset(bar.left + bar.width * 0.5f, bar.center.y))
            frames(scene)
            assertNull(bubble(scene, bar), "the bubble hides when the pointer leaves")
        } finally { scene.close() }
    }

    private fun frames(scene: ImageComposeScene, count: Int = 16) = repeat(count) { scene.render(frame++ * 16_666_667L).close() }
    private fun press(scene: ImageComposeScene, x: Float) = withScene(scene) { bar ->
        scene.sendPointerEvent(PointerEventType.Press, Offset(x, bar.center.y)); frames(scene, 4)
    }
    private fun drag(scene: ImageComposeScene, x: Float) = withScene(scene) { bar ->
        scene.sendPointerEvent(PointerEventType.Move, Offset(x, bar.center.y)); frames(scene, 4)
    }
    private fun release(scene: ImageComposeScene, x: Float) = withScene(scene) { bar ->
        scene.sendPointerEvent(PointerEventType.Release, Offset(x, bar.center.y)); frames(scene, 4)
    }
    private fun withScene(scene: ImageComposeScene, body: (androidx.compose.ui.geometry.Rect) -> Unit) = body(slider(scene).boundsInRoot)

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }
    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)
    private fun texts(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    private fun slider(scene: ImageComposeScene) = nodes(scene).single {
        it.config.getOrNull(SemanticsProperties.ContentDescription)?.any { d -> d.startsWith("Playback position") } == true
    }
    private fun labels(scene: ImageComposeScene) = nodes(scene).filter { texts(it).singleOrNull()?.matches(timePattern) == true }.map { texts(it).first() }
    private fun bubble(scene: ImageComposeScene, bar: androidx.compose.ui.geometry.Rect) = nodes(scene)
        .filter { texts(it).size == 1 && texts(it).first().matches(timePattern) && it.boundsInRoot.bottom <= bar.top }
        .maxByOrNull { it.boundsInRoot.bottom }
    private fun shows(scene: ImageComposeScene, value: String) = nodes(scene).any { texts(it).contains(value) }
    private val timePattern = Regex("-?(\\d+:)?\\d{1,2}:\\d{2}")
    private fun millis(text: String): Long {
        val parts = text.removePrefix("-").split(":").map { it.toLong() }
        return when (parts.size) { 3 -> ((parts[0] * 60 + parts[1]) * 60 + parts[2]) * 1000; else -> (parts[0] * 60 + parts[1]) * 1000 }
    }
    private fun assertAround(actual: Double, expected: Double, tolerance: Double, message: String) =
        assertTrue(abs(actual - expected) <= tolerance, "$message: $actual vs $expected")
}