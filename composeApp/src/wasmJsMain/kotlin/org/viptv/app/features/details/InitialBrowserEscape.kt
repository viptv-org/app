package org.viptv.app.features.details

import androidx.compose.runtime.*
import kotlinx.browser.document
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

@Composable
internal actual fun InitialBrowserEscape(dismiss: () -> Unit) {
    val currentDismiss by rememberUpdatedState(dismiss)
    DisposableEffect(Unit) {
        val handler: (Event) -> Unit = { event ->
            if (event is KeyboardEvent && event.key == "Escape" && document.activeElement == document.body) {
                event.preventDefault()
                currentDismiss()
            }
        }
        document.addEventListener("keyup", handler)
        onDispose { document.removeEventListener("keyup", handler) }
    }
}
