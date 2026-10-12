package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.Screen
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme

fun main() {
    if (System.getenv("VIPTV_UI_PREVIEW") == "1") launchUiPreview() else launchApplication()
}

private fun launchApplication() = application {
    val windowState = rememberWindowState(width = 1100.dp, height = 720.dp)
    Window(onCloseRequest = ::exitApplication, title = "VIPTV Experiment", state = windowState, undecorated = true) {
        val scope = rememberCoroutineScope()
        val controller = remember { ApplicationController(scope) }
        val state by controller.state.collectAsState()
        val mac = remember { System.getProperty("os.name").startsWith("Mac") }
        val base = remember { desktopDataDirectory() }
        var menuOpen by remember { mutableStateOf(false) }
        LaunchedEffect(window) { windowState.isMinimized = false; window.toFront() }
        val minimize = { windowState.isMinimized = true }
        val maximize = {
            windowState.placement = if (windowState.placement == WindowPlacement.Maximized)
                WindowPlacement.Floating else WindowPlacement.Maximized
        }
        ViptvTheme {
            Column(Modifier.fillMaxSize().background(Palette.background)) {
                if (state.screen == Screen.Player) {
                    Box(Modifier.fillMaxSize()) {
                        App(localDatabase = "$base/viptv-experiment/local.sqlite", desktopChrome = true, desktopMenuOpen = menuOpen, providedController = controller)
                        DesktopWindowButtons(mac, minimize, maximize, ::exitApplication, Modifier.align(Alignment.TopEnd))
                    }
                } else {
                    WindowDraggableArea(Modifier.fillMaxWidth()) {
                        DesktopTitleBar(state, mac, controller::navigate, controller::search,
                            { menuOpen = !menuOpen }, minimize, maximize, ::exitApplication, Modifier)
                    }
                    Box(Modifier.weight(1f)) {
                        App(localDatabase = "$base/viptv-experiment/local.sqlite", desktopChrome = true, desktopMenuOpen = menuOpen, providedController = controller)
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
