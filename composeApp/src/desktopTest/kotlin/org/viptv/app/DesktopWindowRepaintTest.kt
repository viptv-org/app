package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import java.awt.GraphicsEnvironment
import java.awt.Robot
import kotlin.test.*

/**
 * app#92: in the real desktop window a hover must reach the *screen*, not only
 * the composition. The window was once made see-through behind Compose's back;
 * on Linux Skiko then presented only the first frame, so hover, like every later
 * repaint, never showed, while the scene tests (`DesktopShellHoverTest`) still
 * passed.
 *
 * Needs a display and moves the real mouse, so it is opt-in:
 * `VIPTV_DESKTOP_WINDOW_TEST=1`, for example under `Xvfb :93` with `DISPLAY=:93`.
 */
class DesktopWindowRepaintTest {
    @Test fun aHoverInTheDesktopWindowReachesTheScreen() {
        assumeTrue("set VIPTV_DESKTOP_WINDOW_TEST=1 with a display",
            System.getenv("VIPTV_DESKTOP_WINDOW_TEST") == "1" && !GraphicsEnvironment.isHeadless())
        val hoverColour = Color(0xFFE81123)
        val robot = Robot()
        val seen = mutableListOf<java.awt.Color>()
        val started = java.util.concurrent.atomic.AtomicBoolean()
        application(exitProcessOnExit = false) {
            val state = rememberWindowState(position = WindowPosition(40.dp, 40.dp), size = DpSize(320.dp, 240.dp))
            DesktopAppWindow(::exitApplication, "app#92 repaint", state, mac = false) {
                val hover = remember { MutableInteractionSource() }
                val hovered by hover.collectIsHoveredAsState()
                Box(Modifier.fillMaxSize().hoverable(hover).background(if (hovered) hoverColour else Color(0xFF0B0B0C)))
                LaunchedEffect(Unit) {
                    // Sample once, however often the window content is composed.
                    if (!started.compareAndSet(false, true)) return@LaunchedEffect
                    // The first composition runs before the window is on screen.
                    while (!window.isShowing || window.width == 0) kotlinx.coroutines.delay(50)
                    val bounds = java.awt.Rectangle(window.locationOnScreen, window.size)
                    val centre = java.awt.Point(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2)
                    withContext(Dispatchers.IO) {
                        fun look(x: Int, y: Int) { robot.mouseMove(x, y); Thread.sleep(1_000); seen += robot.getPixelColor(centre.x, centre.y) }
                        look(bounds.x + bounds.width + 60, bounds.y + bounds.height + 60) // resting, mouse outside
                        look(centre.x, centre.y)                                            // hovered
                        look(bounds.x + bounds.width + 60, bounds.y + bounds.height + 60) // left again
                    }
                    exitApplication()
                }
            }
        }
        val hoverAwt = java.awt.Color(0xE8, 0x11, 0x23)
        assertEquals(3, seen.size, "the window opened and was sampled three times: $seen")
        assertEquals(java.awt.Color(0x0B, 0x0B, 0x0C), seen[0], "at rest the window shows its resting colour: $seen")
        assertEquals(hoverAwt, seen[1], "the hover colour reaches the screen: $seen")
        assertEquals(seen[0], seen[2], "leaving restores the resting colour on screen: $seen")
    }
}
