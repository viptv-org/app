package org.viptv.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.viptv.app.features.home.CardActions
import org.viptv.app.features.home.LocalCardActions
import org.viptv.app.preview.UiPreview
import org.viptv.app.preview.PreviewData
import org.viptv.app.state.ApplicationState
import org.viptv.app.state.Screen
import org.viptv.app.theme.ViptvTheme

internal fun launchUiPreview() = application {
    val phone = System.getenv("VIPTV_UI_PHONE") == "1"
    val width = System.getenv("VIPTV_UI_WIDTH")?.toIntOrNull()?.coerceIn(320, 3840) ?: if (phone) 390 else 1440
    val height = System.getenv("VIPTV_UI_HEIGHT")?.toIntOrNull()?.coerceIn(480, 2160) ?: if (phone) 844 else 900
    val window = rememberWindowState(width = width.dp, height = height.dp)
    var screen by remember { mutableStateOf(runCatching { Screen.valueOf(System.getenv("VIPTV_UI_SCREEN") ?: "Home") }.getOrDefault(Screen.Home)) }
    var term by remember { mutableStateOf(if (screen == Screen.Search) "the w" else "") }
    Window(onCloseRequest = ::exitApplication, title = "VIPTV UI Preview", state = window, undecorated = true) {
        org.viptv.app.player.mpv.MpvWindow(this.window) {
        ViptvTheme {
            Column {
                if (!phone) WindowDraggableArea {
                    DesktopTitleBar(ApplicationState(screen = screen, searchTerm = term, profiles = listOf(PreviewData.profile), selectedProfile = PreviewData.profile.id), false, { screen = it },
                        { term = it; screen = Screen.Search }, {}, { window.isMinimized = true },
                        { window.placement = if (window.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized },
                        ::exitApplication, Modifier, back = if (screen != Screen.Home) { { screen = Screen.Home } } else null,
                        maximized = window.placement == WindowPlacement.Maximized)
                }
                // The hover preview and the right-click menu only render where the
                // desktop chrome published card actions; the harness fakes them so
                // D3HomeHover / D3Context stay reviewable without an account.
                val previewActions = remember { CardActions(play = {}, toggleSave = {}, isSaved = { false }) }
                if (phone) UiPreview(desktop = false, screen = screen, navigate = { screen = it }, searchTerm = term, search = { term = it }, tvDetailVariant = System.getenv("VIPTV_UI_VARIANT") ?: "Movie")
                else CompositionLocalProvider(LocalCardActions provides previewActions) {
                    UiPreview(desktop = true, screen = screen, navigate = { screen = it }, searchTerm = term, search = { term = it }, tvDetailVariant = System.getenv("VIPTV_UI_VARIANT") ?: "Movie")
                }
            }
        }
        }
    }
}
