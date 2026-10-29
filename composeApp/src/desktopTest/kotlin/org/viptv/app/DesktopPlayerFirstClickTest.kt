package org.viptv.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import org.viptv.app.features.player.PlayerScreen
import org.viptv.app.player.PlayerFact
import org.viptv.app.theme.ViptvTheme
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.InputEvent
import java.awt.Point
import java.awt.Robot
import java.awt.Window
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class DesktopPlayerFirstClickTest {
    private val width = 1280
    private val height = 720

    @Test fun theFirstPointerPressOnAFreshWindowPauses() {
        assumeTrue("set VIPTV_DESKTOP_WINDOW_TEST=1 with a display",
            System.getenv("VIPTV_DESKTOP_WINDOW_TEST") == "1" && !GraphicsEnvironment.isHeadless())
        val pause = pointerTarget("Pause")
        val toggles = AtomicInteger()
        application(exitProcessOnExit = false) {
            val state = rememberWindowState(position = WindowPosition(40.dp, 40.dp), size = DpSize(width.dp, height.dp))
            var paused by remember { mutableStateOf(false) }
            DesktopAppWindow(::exitApplication, "app#98 first click", state, framed = true) {
                PlayerScreen(null, paused, null, { paused = !paused; toggles.incrementAndGet() }, {}, {},
                    preview = PlayerFact.Progress(0, 20_000))
                LaunchedEffect(Unit) {
                    while (!window.isShowing || window.width == 0) delay(50)
                    repeat(20) { if (!window.isFocused) delay(50) }
                    // The window must own the pointer before it is clicked, or the activating press never reaches the transport.
                    assertTrue(window.isFocused, "a freshly opened player window must take focus")
                    withContext(Dispatchers.IO) { clickAt(window, pause) }
                    delay(400)
                    exitApplication()
                }
            }
        }
        assertEquals(1, toggles.get(), "the first press, on a window never clicked before, must reach Pause")
    }

    @Test fun theTransportTakesAPointerPressAndTheTimelineSeeksAtTheClick() {
        var toggles = 0
        EventQueue.invokeAndWait {
            val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
                PlayerScreen(null, false, null, { toggles++ }, {}, {}, preview = PlayerFact.Progress(0, 20_000))
            } }
            try {
                frames(scene)
                press(scene, node(scene, "Pause").boundsInWindow.center)
                assertEquals(1, toggles, "the Pause press toggles playback")
                press(scene, node(scene, "Playback position").boundsInWindow.center)
                assertTrue(nodes(scene).any { text(it) == "0:10" }, "the timeline click seeks to half of 0:20")
            } finally { scene.close() }
        }
    }

    private fun pointerTarget(label: String): Offset {
        var found: Offset? = null
        EventQueue.invokeAndWait {
            val scene = ImageComposeScene(width, height, Density(1f)) { ViptvTheme {
                PlayerScreen(null, false, null, {}, {}, {}, preview = PlayerFact.Progress(0, 20_000))
            } }
            try {
                frames(scene)
                val bounds = node(scene, label).boundsInWindow
                found = Offset(bounds.center.x, bounds.center.y)
            } finally { scene.close() }
        }
        return requireNotNull(found)
    }

    private fun clickAt(window: Window, at: Offset) {
        val point = SwingUtilities.convertPointToScreen(Point(at.x.toInt(), at.y.toInt()), window.contentPane)
        val robot = Robot()
        robot.mouseMove(point.x, point.y)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    }

    private fun frames(scene: ImageComposeScene) = repeat(16) { scene.render(it * 16_666_667L).close() }

    private fun press(scene: ImageComposeScene, at: Offset) {
        scene.sendPointerEvent(PointerEventType.Press, at)
        scene.sendPointerEvent(PointerEventType.Release, at)
        frames(scene)
    }

    private fun node(scene: ImageComposeScene, label: String) = nodes(scene).single {
        (it.config.contains(SemanticsActions.OnClick) || it.config.contains(SemanticsActions.SetProgress)) &&
            (text(it) == label || it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true)
    }

    private fun nodes(scene: ImageComposeScene) = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }

    private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)

    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
}
