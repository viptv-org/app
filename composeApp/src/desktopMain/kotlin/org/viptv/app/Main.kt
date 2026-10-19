package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import org.viptv.app.state.*
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme

fun main() {
    if (System.getenv("VIPTV_ENGINE_SMOKE_TEST") == "1") { verifyPackagedLocalEngine(); return }
    if (System.getenv("VIPTV_DESKTOP_PLAYER_QA") == "1") { launchDesktopPlayerQa(); return }
    if (System.getenv("VIPTV_UI_PREVIEW") == "1") launchUiPreview() else launchApplication()
}

private fun launchApplication() = application {
    val windowState = rememberWindowState(width = 1100.dp, height = 720.dp)
    Window(onCloseRequest = ::exitApplication, title = "VIPTV Experiment", state = windowState, undecorated = true) {
        // A hairline outline and rounded corners are what make an undecorated
        // window read as a native app instead of a pasted-on panel. The window
        // itself is made transparent so the corners actually round off; if the
        // platform refuses, the app still renders, just square.
        LaunchedEffect(Unit) { runCatching { window.background = java.awt.Color(0, 0, 0, 0) } }
        val scope = rememberCoroutineScope()
        val controller = remember { ApplicationController(scope) }
        val state by controller.state.collectAsState()
        val mac = remember { System.getProperty("os.name").startsWith("Mac") }
        val base = remember { desktopDataDirectory() }
        var menuOpen by remember { mutableStateOf(false) }
        LaunchedEffect(window) {
            windowState.isMinimized = false; window.toFront()
            window.minimumSize = Dimension(420, 360)
        }
        val minimize = { windowState.isMinimized = true }
        val maximize = {
            windowState.placement = if (windowState.placement == WindowPlacement.Maximized)
                WindowPlacement.Floating else WindowPlacement.Maximized
        }
        ViptvTheme {
            // Native app framing: a rounded root surface with a hairline outline.
            // macOS already rounds and outlines its own windows, so it keeps the
            // plain surface.
            val windowShape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            val framed = if (mac) Modifier else Modifier
                .clip(windowShape)
                .background(Palette.background)
                .border(1.dp, Color.White.copy(alpha = 0.09f), windowShape)
            Box(Modifier.fillMaxSize().then(framed)) {
            Column(Modifier.fillMaxSize()) {
                if (state.screen == Screen.Player) {
                    Box(Modifier.fillMaxSize()) {
                        App(localDatabase = "$base/viptv-experiment/local.sqlite", desktopChrome = true, desktopMenuOpen = menuOpen, providedController = controller)
                        DesktopWindowButtons(mac, minimize, maximize, ::exitApplication, Modifier.align(Alignment.TopEnd))
                    }
                } else {
                    WindowDraggableArea(Modifier.fillMaxWidth()) {
                        DesktopTitleBar(state, mac, controller::navigate, controller::search,
                            { menuOpen = !menuOpen }, minimize, maximize, ::exitApplication, Modifier,
                            back = if (state.screen != Screen.Home) controller::navigateBack else null)
                    }
                    Box(Modifier.weight(1f)) {
                        App(localDatabase = "$base/viptv-experiment/local.sqlite", desktopChrome = true, desktopMenuOpen = menuOpen, providedController = controller)
                    }
                }
            }
            }
        }
    }
}

private fun desktopDataDirectory(): String = when {
    System.getProperty("os.name").startsWith("Windows") -> System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
    System.getProperty("os.name").startsWith("Mac") -> System.getProperty("user.home") + "/Library/Application Support"
    else -> System.getenv("XDG_DATA_HOME") ?: System.getProperty("user.home") + "/.local/share"
}
