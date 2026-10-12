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
    val screen = runCatching { Screen.valueOf(window.location.hash.substringAfter("/", "Home")) }.getOrDefault(Screen.Home)
    ComposeViewport(document.getElementById("viptv-ui-layer") as HTMLElement) {
        if (window.location.hash == "#playback-qa") PlaybackQa(canvasColor = Color.Transparent)
        else if (preview) UiPreview(initialScreen = screen, browser = true)
        else App(browser = true)
    }
}
