package org.viptv.app

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.theme.ViptvTheme
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.event.InputEvent
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopTitleBarWindowDragTest {
    @Test fun nativeWindowMovesAndRetainsDoubleClickAndButtons() {
        assumeTrue(System.getenv("VIPTV_DESKTOP_WINDOW_TEST") == "1" && !GraphicsEnvironment.isHeadless())
        val doubles = AtomicInteger()
        val closes = AtomicInteger()
        var failure: Throwable? = null
        application(exitProcessOnExit = false) {
            val state = rememberWindowState(position = WindowPosition(40.dp, 40.dp), size = DpSize(800.dp, 300.dp))
            DesktopAppWindow(::exitApplication, "VIPTV title-bar test", state, framed = true) {
                ViptvTheme(pointer = true) {
                    WindowDraggableArea {
                        DesktopTitleBar(ApplicationState(screen = Screen.Connect), false,
                            {}, {}, {}, {}, {}, { closes.incrementAndGet() }, Modifier,
                            onDoubleClick = { doubles.incrementAndGet() })
                    }
                }
                LaunchedEffect(window) {
                    try {
                        withTimeout(5_000) { while (!window.isShowing || !window.isFocused) delay(10) }
                        withContext(Dispatchers.IO) {
                            val robot = Robot().apply { autoDelay = 40 }
                            robot.waitForIdle()
                            val before = window.location
                            robot.mouseMove(before.x + 300, before.y + 20)
                            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                            repeat(10) { step -> robot.mouseMove(before.x + 300 + (step + 1) * 10, before.y + 20 + (step + 1) * 5) }
                            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                            robot.waitForIdle()
                            val moved = window.location
                            assertTrue(moved.x >= before.x + 80 && moved.y >= before.y + 30,
                                "The native window must follow its title bar: $before -> $moved")
                            robot.mouseMove(moved.x + 300, moved.y + 20)
                            repeat(2) {
                                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                            }
                            robot.waitForIdle()
                            assertEquals(1, doubles.get())
                            robot.mouseMove(moved.x + 777, moved.y + 20)
                            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                            robot.waitForIdle()
                            assertEquals(1, closes.get())
                            assertEquals(1, doubles.get())
                        }
                    } catch (error: Throwable) { failure = error }
                    finally { exitApplication() }
                }
            }
        }
        failure?.let { throw it }
    }
}
