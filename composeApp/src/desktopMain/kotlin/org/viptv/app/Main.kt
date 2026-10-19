package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import org.viptv.app.features.home.CardActions
import org.viptv.app.features.home.LocalCardActions
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
    val searchFocus = remember { FocusRequester() }
    Window(onCloseRequest = ::exitApplication, title = "VIPTV Experiment", state = windowState, undecorated = true,
        onPreviewKeyEvent = { event ->
            // Ctrl/⌘+K is advertised in the title bar; it has to actually work.
            if (event.type == KeyEventType.KeyDown && (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.K) {
                runCatching { searchFocus.requestFocus() }
                true
            } else false
        }) {
        // A hairline outline and rounded corners are what make an undecorated
        // window read as a native app instead of a pasted-on panel. The window
        // itself is made transparent so the corners actually round off; if the
        // platform refuses, the app still renders, just square.
        // The transparent frame is not only a startup concern: after every
        // placement change (maximize/restore) the native panel repaints its own
        // opaque background before Compose's rounded surface catches up, which is
        // the square-corner flash. Keying the effect on placement re-asserts it,
        // and the root/content panes have to be non-opaque or the frame's own
        // background still paints the corners square.
        DisposableEffect(window, windowState.placement) {
            runCatching {
                val clear = java.awt.Color(0, 0, 0, 0)
                window.background = clear
                window.rootPane.isOpaque = false
                window.rootPane.background = clear
                (window.contentPane as? javax.swing.JComponent)?.let { it.isOpaque = false; it.background = clear }
            }
            onDispose { }
        }
        val scope = rememberCoroutineScope()
        val controller = remember { ApplicationController(scope) }
        val state by controller.state.collectAsState()
        val mac = remember { System.getProperty("os.name").startsWith("Mac") }
        val base = remember { desktopDataDirectory() }
        var menuOpen by remember { mutableStateOf(false) }
        var offlineDismissed by remember { mutableStateOf(false) }
        // A reconnect (or a different failure) clears the flag, so the next outage
        // shows the bar again.
        LaunchedEffect(state.error) { if (state.error != ServerUnreachableMessage) offlineDismissed = false }
        LaunchedEffect(window) {
            windowState.isMinimized = false; window.toFront()
            window.minimumSize = Dimension(420, 360)
        }
        val density = LocalDensity.current
        val maximized = windowState.placement == WindowPlacement.Maximized
        val minimize = { windowState.isMinimized = true }
        // Compose reports the maximised bounds back into WindowState, so restoring
        // to Floating alone leaves a screen-sized "floating" window. Keep the
        // floating bounds and put them back once the placement has actually
        // dropped, in a separate effect — setting size in the same click is
        // swallowed while the window is still maximised.
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
        ViptvTheme {
            // Native app framing: a rounded root surface with a hairline outline.
            // macOS already rounds and outlines its own windows, so it keeps the
            // plain surface.
            val windowShape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
            val framed = if (mac) Modifier else Modifier
                .clip(windowShape)
                .background(Palette.background)
                .border(1.dp, Color.White.copy(alpha = 0.09f), windowShape)
            // NAV-15: the transparent AWT frame composites as an opaque black
            // rectangle on an X11 host without a compositor. Paint the window's
            // own surface underneath so no unpainted pixel can go black; the
            // rounded layer stays on top where transparency is available.
            Box(Modifier.fillMaxSize().background(Palette.background)) {
            Box(Modifier.fillMaxSize().then(framed)) {
            Column(Modifier.fillMaxSize()) {
                if (state.screen == Screen.Player) {
                    Box(Modifier.fillMaxSize()) {
                        App(localDatabase = "$base/viptv-experiment/local.sqlite", desktopChrome = true, desktopMenuOpen = menuOpen, providedController = controller)
                        DesktopWindowButtons(mac, minimize, maximize, ::exitApplication, Modifier.align(Alignment.TopEnd), maximized = maximized)
                    }
                } else {
                    WindowDraggableArea(Modifier.fillMaxWidth()) {
                        DesktopTitleBar(state, mac, controller::navigate, controller::search,
                            { menuOpen = !menuOpen }, minimize, maximize, ::exitApplication, Modifier,
                            back = if (state.navBack.isNotEmpty() || state.screen != Screen.Home) controller::navigateBack else null,
                            forward = if (state.navForward.isNotEmpty()) controller::navigateForward else null,
                            searchFocus = searchFocus,
                            maximized = maximized)
                    }
                    Box(Modifier.weight(1f)) {
                        // DUI-04: the card hover preview and right-click menu need
                        // Play / My List; publishing them once here keeps
                        // HomeCatalogRow and MediaCard signatures untouched.
                        CompositionLocalProvider(LocalCardActions provides CardActions(
                            play = controller::playTitle,
                            toggleSave = controller::toggleFavorite,
                            isSaved = { state.isSaved(it) })) {
                            App(localDatabase = "$base/viptv-experiment/local.sqlite", desktopChrome = true, desktopMenuOpen = menuOpen, providedController = controller)
                        }
                        // Board D3Offline: overlays the content area below the title
                        // bar and leaves the 72 dp rail visible. Home only — that is
                        // the page whose cached rows the copy promises.
                        if (state.screen == Screen.Home && state.error == ServerUnreachableMessage && !offlineDismissed) DesktopOfflineBar(
                            retry = controller::refresh,
                            dismiss = { offlineDismissed = true },
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 72.dp))
                    }
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
