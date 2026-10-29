package org.viptv.app

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
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
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Robot
import java.awt.Window
import java.awt.event.InputEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class DesktopPlayerWindowFirstClickTest {
    @Test fun theFirstPointerPressOnAFreshWindowPauses() {
        assumeTrue("set VIPTV_DESKTOP_WINDOW_TEST=1 with a display",
            System.getenv("VIPTV_DESKTOP_WINDOW_TEST") == "1" && !GraphicsEnvironment.isHeadless())
        val pause = playerPausePointer(1280, 720)
        val toggles = AtomicInteger()
        application(exitProcessOnExit = false) {
            val state = rememberWindowState(position = WindowPosition(40.dp, 40.dp), size = DpSize(1280.dp, 720.dp))
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

    private fun clickAt(window: Window, at: Offset) {
        val content = (window as? javax.swing.RootPaneContainer)?.contentPane ?: window
        val point = Point(at.x.toInt(), at.y.toInt())
        SwingUtilities.convertPointToScreen(point, content)
        val robot = Robot()
        robot.mouseMove(point.x, point.y)
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    }
}
