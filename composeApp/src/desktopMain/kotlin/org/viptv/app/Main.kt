package org.viptv.app

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import kotlinx.coroutines.launch
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.SourceMemory
import org.viptv.app.state.closeForExit

fun main() {
    SourceMemory.useDeviceStorage()
    if (System.getenv("VIPTV_MPV_SMOKE_TEST") != null) { org.viptv.app.player.mpv.verifyPackagedMpv(); return }
    if (System.getenv("VIPTV_TORRENT_SMOKE_TEST") != null) { verifyPackagedTorrentRuntime(); return }
    if (System.getenv("VIPTV_TORRENT_BENCH_INPUT") != null) { launchDesktopTorrentBenchmark(); return }
    if (System.getenv("VIPTV_ENGINE_SMOKE_TEST") == "1") { verifyPackagedLocalEngine(); return }
    if (System.getenv("VIPTV_DESKTOP_PLAYER_QA") == "1") { launchDesktopPlayerQa(); return }
    if (System.getenv("VIPTV_UI_PREVIEW") == "1") launchUiPreview() else launchApplication()
}

private fun launchApplication() = application {
    val windowState = rememberWindowState(width = 1100.dp, height = 720.dp)
    val mac = remember { System.getProperty("os.name").startsWith("Mac") }
    val scope = rememberCoroutineScope()
    val controller = remember { ApplicationController(scope) }
    var visible by remember { mutableStateOf(true) }
    var closing by remember { mutableStateOf(false) }
    val requestClose = {
        if (!closing) {
            closing = true
            visible = false
            scope.launch { controller.closeForExit(3_000) { exitApplication() } }
        }
    }
    DesktopAppWindow(requestClose, "VIPTV Experiment", windowState, framed = !mac, visible = visible) {
        val globalBack = remember(controller) { GlobalBack(controller) }
        val base = remember { desktopDataDirectory() }
        LaunchedEffect(window) {
            windowState.isMinimized = false; window.toFront()
            window.minimumSize = Dimension(420, 360)
        }
        val density = LocalDensity.current
        val placement = remember { DesktopWindowPlacement() }
        fun currentBounds(): FloatingBounds = windowState.size to with(density) { DpOffset(window.x.toDp(), window.y.toDp()) }
        // Restore the floating bounds in a separate effect: setting the size in the same click is swallowed while maximised or fullscreen.
        LaunchedEffect(windowState.placement) {
            if (windowState.placement == WindowPlacement.Floating) {
                placement.takeFloatingBounds()?.let { (size, position) ->
                    windowState.size = size
                    windowState.position = WindowPosition(position.x, position.y)
                }
            }
        }
        val maximize = {
            placement.maximize(currentBounds())
            windowState.placement = placement.placement
        }
        val toggleFullscreen = {
            placement.toggleFullscreen(currentBounds())
            windowState.placement = placement.placement
        }
        val controls = DesktopWindowControls(minimize = { windowState.isMinimized = true }, maximize = maximize,
            close = requestClose, maximized = placement.maximized, fullscreen = placement.fullscreen, toggleFullscreen = toggleFullscreen)
        org.viptv.app.player.mpv.MpvWindow(window) {
            DesktopShell(controller, globalBack, mac, controls, localDatabase = "$base/viptv-experiment/local.sqlite",
                titleBarArea = { bar -> WindowDraggableArea(Modifier.fillMaxWidth()) { bar() } })
        }
    }
}

private fun desktopDataDirectory(): String = when {
    System.getProperty("os.name").startsWith("Windows") -> System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
    System.getProperty("os.name").startsWith("Mac") -> System.getProperty("user.home") + "/Library/Application Support"
    else -> System.getenv("XDG_DATA_HOME") ?: System.getProperty("user.home") + "/.local/share"
}
