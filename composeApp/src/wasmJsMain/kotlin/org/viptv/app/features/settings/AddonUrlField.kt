package org.viptv.app.features.settings

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement

/** Native editing avoids the canvas text-input mirror reordering rapid URL keystrokes. */
@Composable
internal actual fun AddonUrlField(state: TextFieldState, enabled: Boolean, modifier: Modifier, dismiss: (() -> Unit)?) {
    val currentDismiss by rememberUpdatedState(dismiss)
    HtmlElementView(factory = {
        (document.createElement("input") as HTMLInputElement).apply {
            type = "url"
            setAttribute("aria-label", "Add-on URL")
            setAttribute("autocomplete", "off")
            setAttribute("autocapitalize", "off")
            setAttribute("spellcheck", "false")
            placeholder = "https://addon.example/manifest.json"
            style.cssText = "box-sizing:border-box;width:100%;height:100%;border:1px solid #69696B;border-radius:12px;background:transparent;color:#F4F2EE;padding:0 14px;font:16px Onest,sans-serif;outline:none;"
            oninput = { state.edit { replace(0, length, value) }; null }
            onkeydown = { event ->
                if (event.key == "Escape" && currentDismiss != null) {
                    event.preventDefault(); event.stopPropagation(); currentDismiss?.invoke()
                }
                null
            }
            onfocus = { style.borderColor = "#F5C542"; null }
            onblur = { style.borderColor = "#69696B"; null }
        }
    }, modifier = modifier.height(56.dp), update = { input ->
        input.disabled = !enabled
        input.style.opacity = if (enabled) "1" else "0.5"
        // Never reset the native caret or composition when mirroring its own input event.
        val current = state.text.toString()
        if (input.value != current) input.value = current
    }, onRelease = { input -> input.oninput = null; input.onkeydown = null; input.onfocus = null; input.onblur = null })
}
