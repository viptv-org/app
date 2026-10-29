package org.viptv.app

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import org.viptv.app.state.ApplicationController

fun main() {
    if (System.getenv("VIPTV_MPV_SMOKE_TEST") != null) { org.viptv.app.player.mpv.verifyPackagedMpv(); return }
    if (System.getenv("VIPTV_TORRENT_SMOKE_TEST") != null) { verifyPackagedTorrentRuntime(); return }
    if (System.getenv("VIPTV_TORRENT_BENCH_INPUT") != null) { launchDesktopTorrentBenchmark(); return }
    if (System.getenv("VIPTV_ENGINE_SMOKE_TEST") == "1") { verifyPackagedLocalEngine(); return }
    if (System.getenv("VIPTV_DESKTOP_PLAYER_QA") == "1") { launchDesktopPlayerQa(); return }
    if (System.getenv("VIPTV_UI_PREVIEW") == "1") launchUiPreview() else launchApplication()
}

private fun launchApplication() = application {
    val windowState = rememberWindowState(width = 1100.dp, height = 720.dp)
    val searchFocus = remember { FocusRequester() }
    val mac = remember { System.getProperty("os.name").startsWith("Mac") }
    DesktopAppWindow(::exitApplication, "VIPTV Experiment", windowState, framed = !mac,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.K) {
                runCatching { searchFocus.requestFocus() }
                true
            } else false
        }) {
        val scope = rememberCoroutineScope()
        val controller = remember { ApplicationController(scope) }
        val globalBack = remember(controller) { GlobalBack(controller) }
        val base = remember { desktopDataDirectory() }
        LaunchedEffect(window) {
            windowState.isMinimized = false; window.toFront()
            window.minimumSize = Dimension(420, 360)
        }
        val density = LocalDensity.current
        val maximized = windowState.placement == WindowPlacement.Maximized
        // Restore the floating bounds in a separate effect: setting the size in the same click is swallowed while maximised.
        var restoreTo by remember { mutableStateOf<Pair<DpSize, DpOffset>?>(null) }
        LaunchedEffect(windowState.placement) {
            if (windowState.placement == WindowPlacement.Floating) {
                restoreTo?.let { (size, position) ->
                    windowState.size = size
                    windowState.position = WindowPosition(position.x, position.y)
                }
                restoreTo = null
            }
        }
        val maximize = {
            if (maximized) {
                windowState.placement = WindowPlacement.Floating
            } else {
                restoreTo = windowState.size to with(density) { DpOffset(window.x.toDp(), window.y.toDp()) }
                windowState.placement = WindowPlacement.Maximized
            }
        }
        val controls = DesktopWindowControls(minimize = { windowState.isMinimized = true }, maximize = maximize,
            close = ::exitApplication, maximized = maximized)
        org.viptv.app.player.mpv.MpvWindow(window) {
            DesktopShell(controller, globalBack, mac, controls, localDatabase = "$base/viptv-experiment/local.sqlite",
                searchFocus = searchFocus, titleBarArea = { bar -> WindowDraggableArea(Modifier.fillMaxWidth()) { bar() } })
        }
    }
}

private fun desktopDataDirectory(): String = when {
    System.getProperty("os.name").startsWith("Windows") -> System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
    System.getProperty("os.name").startsWith("Mac") -> System.getProperty("user.home") + "/Library/Application Support"
    else -> System.getenv("XDG_DATA_HOME") ?: System.getProperty("user.home") + "/.local/share"
}
