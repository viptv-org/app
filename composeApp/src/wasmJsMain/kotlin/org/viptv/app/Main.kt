package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import androidx.compose.ui.graphics.Color
import org.viptv.app.preview.UiPreview
import org.viptv.app.preview.PlaybackQa
import org.viptv.app.state.DeviceApprovalController
import org.viptv.app.state.Screen
import org.viptv.app.features.entry.DeviceApprovalPage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette
import org.viptv.app.theme.ViptvTheme

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val preview = window.location.hash.startsWith("#ui-preview")
    val previewParts = window.location.hash.removePrefix("#").split("/")
    val screen = runCatching { Screen.valueOf(previewParts.getOrNull(1) ?: "Home") }.getOrDefault(Screen.Home)
    val variant = previewParts.getOrNull(2) ?: "Movie"
    ComposeViewport(document.getElementById("viptv-ui-layer") as HTMLElement) {
        when {
            window.location.hash == "#playback-qa" -> PlaybackQa(canvasColor = Color.Transparent)
            preview -> UiPreview(initialScreen = screen, browser = true, tvDetailVariant = variant)
            window.location.pathname.trimEnd('/').endsWith("/device") -> DeviceApprovalRoute()
            else -> BrowserApp()
        }
    }
}

/** The /device route: TV sign-in approval with account-only authentication. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DeviceApprovalRoute() {
    val scope = rememberCoroutineScope()
    val controller = remember { DeviceApprovalController(scope) }
    DisposableEffect(controller) { onDispose { controller.reset() } }
    ViptvTheme(television = false) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background)) {
            val compact = maxWidth < 640.dp
            val initialCode = remember { window.location.search.removePrefix("?").split("&")
                .firstNotNullOfOrNull { it.split("=", limit = 2).takeIf { parts -> parts.size == 2 && parts[0] == "code" } }
                ?.let { parts -> parts[1] } ?: "" }
            DeviceApprovalPage(compact, controller, initialCode) {
                window.location.href = "/"
            }
        }
    }
}
