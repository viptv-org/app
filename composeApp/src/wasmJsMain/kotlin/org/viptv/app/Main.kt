package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import androidx.compose.ui.graphics.Color
import org.viptv.app.preview.UiPreview
import org.viptv.app.preview.PlaybackQa
import org.viptv.app.state.Screen

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val preview = window.location.hash.startsWith("#ui-preview")
    val previewParts = window.location.hash.removePrefix("#").split("/")
    val screen = runCatching { Screen.valueOf(previewParts.getOrNull(1) ?: "Home") }.getOrDefault(Screen.Home)
    val variant = previewParts.getOrNull(2) ?: "Movie"
    ComposeViewport(document.getElementById("viptv-ui-layer") as HTMLElement) {
        if (window.location.hash == "#playback-qa") PlaybackQa(canvasColor = Color.Transparent)
        else if (preview) UiPreview(initialScreen = screen, browser = true, tvDetailVariant = variant)
        else App(browser = true)
    }
}
