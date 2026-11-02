package org.viptv.app.components

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLStyleElement
import kotlin.math.roundToInt

private fun ensureDomTextStyle() {
    if (document.getElementById("viptv-dom-text-style") != null) return
    val style = (document.createElement("style") as HTMLStyleElement).apply {
        id = "viptv-dom-text-style"
        textContent = ".viptv-dom-text::placeholder { color:#8E8C88; opacity:1; }"
    }
    document.body?.appendChild(style)
}

private fun Color.css() = "rgba(${(red * 255).roundToInt()},${(green * 255).roundToInt()},${(blue * 255).roundToInt()},$alpha)"

private fun inputMode(type: KeyboardType): String? = when (type) {
    KeyboardType.Number, KeyboardType.NumberPassword -> "numeric"
    KeyboardType.Decimal -> "decimal"
    KeyboardType.Phone -> "tel"
    KeyboardType.Email -> "email"
    KeyboardType.Uri -> "url"
    else -> null
}

private fun HTMLInputElement.show(spec: DomTextSpec, fontPx: Float) {
    type = if (spec.secret) "password" else "text"
    inputMode(spec.keyboardType)?.let { setAttribute("inputmode", it) } ?: removeAttribute("inputmode")
    setAttribute("aria-label", spec.label)
    placeholder = spec.placeholder.orEmpty()
    disabled = !spec.enabled
    style.opacity = if (spec.enabled) "1" else "0.5"
    style.color = spec.textColor.css()
    style.setProperty("caret-color", spec.textColor.css())
    style.fontSize = "${fontPx}px"
    // Never reset the native caret or composition when mirroring its own input event.
    if (value != spec.value) value = spec.value
}

internal object BrowserDomTextHost : DomTextHost {
    @Composable
    override fun Field(spec: DomTextSpec, modifier: Modifier, decoration: @Composable (input: @Composable (Modifier) -> Unit) -> Unit) {
        val latest by rememberUpdatedState(spec)
        val density = LocalDensity.current
        val fontPx = with(density) { spec.fontSize.toDp().value }
        val lineHeight = with(density) { (spec.fontSize.toDp() * 1.5f) }
        val focus = remember { mutableListOf<FocusInteraction.Focus>() }
        Box(modifier) {
            decoration { inputModifier ->
                HtmlElementView(factory = {
                    ensureDomTextStyle()
                    (document.createElement("input") as HTMLInputElement).apply {
                        className = "viptv-dom-text"
                        setAttribute("autocomplete", "off")
                        setAttribute("autocapitalize", "off")
                        setAttribute("autocorrect", "off")
                        setAttribute("spellcheck", "false")
                        style.cssText = "box-sizing:border-box;width:100%;height:100%;margin:0;padding:0;border:none;outline:none;" +
                            "background:transparent;font-family:Onest,sans-serif;"
                        oninput = { latest.onValueChange(value); null }
                        onkeydown = { event ->
                            val enter = latest.onEnter
                            if (event.key == "Enter" && !event.isComposing && enter != null) {
                                event.preventDefault(); enter()
                            }
                            null
                        }
                        onfocus = {
                            val entered = FocusInteraction.Focus()
                            focus += entered
                            latest.interactionSource?.tryEmit(entered)
                            null
                        }
                        onblur = {
                            focus.removeFirstOrNull()?.let { latest.interactionSource?.tryEmit(FocusInteraction.Unfocus(it)) }
                            null
                        }
                    }
                }, modifier = inputModifier.fillMaxWidth().height(lineHeight), update = { input ->
                    input.show(spec, fontPx)
                }, onRelease = { input ->
                    input.oninput = null; input.onkeydown = null; input.onfocus = null; input.onblur = null
                })
            }
        }
    }
}
