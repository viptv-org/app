@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.components

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLFormElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLStyleElement

private class CredentialFormNodes {
    val inputs = mutableListOf<HTMLInputElement>()
    var submit: HTMLButtonElement? = null
    var secondary: HTMLButtonElement? = null
    var errorRow: HTMLDivElement? = null
}

private const val inputHeight = 56
private const val gap = 14
private const val buttonHeight = 40
private const val errorHeight = 20

private fun ensureCredentialStyle() {
    if (document.getElementById("viptv-credential-style") != null) return
    val style = (document.createElement("style") as HTMLStyleElement).apply {
        id = "viptv-credential-style"
        textContent = """
            .viptv-cred-form { display:flex; flex-direction:column; gap:${gap}px; width:100%; margin:0; }
            .viptv-cred-input { box-sizing:border-box; width:100%; height:${inputHeight}px; background:transparent; color:#F4F2EE; border:1px solid #B6B4AF; border-radius:4px; padding:0 16px; font:16px Onest,sans-serif; outline:none; }
            .viptv-cred-input::placeholder { color:#B6B4AF; }
            .viptv-cred-input:focus { border:2px solid #F4F2EE; padding:0 15px; }
            .viptv-cred-input[data-error="true"] { border-color:#FF7A6E; }
            .viptv-cred-input[data-error="true"]:focus { border-color:#FF7A6E; }
            .viptv-cred-input:disabled { opacity:.5; }
            .viptv-cred-error { height:${errorHeight}px; line-height:${errorHeight}px; color:#FF7A6E; font:14px Onest,sans-serif; overflow:hidden; }
            .viptv-cred-actions { display:flex; flex-direction:row; gap:12px; align-items:center; height:${buttonHeight}px; }
            .viptv-cred-submit { height:${buttonHeight}px; padding:0 24px; border:none; border-radius:20px; background:#F4F2EE; color:#0B0B0C; font:600 14px Onest,sans-serif; cursor:pointer; }
            .viptv-cred-submit:disabled { opacity:.5; cursor:default; }
            .viptv-cred-secondary { height:${buttonHeight}px; padding:0 16px; border:none; background:transparent; color:#F4F2EE; font:500 14px Onest,sans-serif; cursor:pointer; }
            .viptv-cred-secondary:disabled { opacity:.5; cursor:default; }
        """.trimIndent()
    }
    document.body?.appendChild(style)
}

@JsFun("""(id, password) => {
    if (typeof window.PasswordCredential !== 'function' || !navigator.credentials) return;
    navigator.credentials.store(new window.PasswordCredential({ id, password }));
}""")
private external fun browserStorePassword(id: String, password: String)

@Composable
internal actual fun CredentialForm(
    fields: List<CredentialField>,
    submitLabel: String,
    valid: Boolean,
    busy: Boolean,
    error: String?,
    onChange: (Int, String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier,
    showProgress: Boolean,
    secondaryLabel: String?,
    onSecondary: (() -> Unit)?,
    storePasswordOnSuccess: Boolean,
) {
    val latestOnChange by rememberUpdatedState(onChange)
    val latestOnSubmit by rememberUpdatedState(onSubmit)
    val latestOnSecondary by rememberUpdatedState(onSecondary)
    val nodes = remember { CredentialFormNodes() }
    var pendingStore by remember { mutableStateOf<Pair<String, String>?>(null) }
    val formHeight: Dp = (inputHeight * fields.size + gap * (fields.size + 1) + errorHeight + buttonHeight).dp

    HtmlElementView(
        factory = {
            ensureCredentialStyle()
            val form = (document.createElement("form") as HTMLFormElement).apply {
                className = "viptv-cred-form"
                setAttribute("method", "post")
                setAttribute("autocomplete", "on")
            }
            nodes.inputs.clear()
            nodes.submit = null
            nodes.secondary = null
            nodes.errorRow = null
            fields.forEachIndexed { index, field ->
                val input = (document.createElement("input") as HTMLInputElement).apply {
                    type = if (field.secret) "password" else "text"
                    name = field.name
                    setAttribute("autocomplete", field.autocomplete)
                    setAttribute("aria-label", field.label)
                    placeholder = field.label
                    className = "viptv-cred-input"
                    value = field.value
                    disabled = busy
                    setAttribute("data-error", if (field.errorText != null) "true" else "false")
                }
                input.oninput = { latestOnChange(index, input.value); null }
                nodes.inputs += input
                form.appendChild(input)
            }
            val errorRow = (document.createElement("div") as HTMLDivElement).apply {
                className = "viptv-cred-error"
                textContent = error ?: ""
            }
            nodes.errorRow = errorRow
            form.appendChild(errorRow)
            val actions = document.createElement("div").apply { className = "viptv-cred-actions" }
            val submit = (document.createElement("button") as HTMLButtonElement).apply {
                type = "submit"
                className = "viptv-cred-submit"
                textContent = submitLabel
                disabled = !valid || busy
            }
            nodes.submit = submit
            actions.appendChild(submit)
            if (secondaryLabel != null && onSecondary != null) {
                val secondary = (document.createElement("button") as HTMLButtonElement).apply {
                    type = "button"
                    className = "viptv-cred-secondary"
                    textContent = secondaryLabel
                    disabled = busy
                }
                secondary.onclick = { latestOnSecondary?.invoke(); null }
                nodes.secondary = secondary
                actions.appendChild(secondary)
            }
            form.appendChild(actions)
            form.onsubmit = { event ->
                event.preventDefault()
                if (storePasswordOnSuccess) {
                    val username = nodes.inputs.firstOrNull { it.getAttribute("autocomplete") == autocompleteUsername }?.value
                    val password = nodes.inputs.firstOrNull { it.getAttribute("autocomplete") == autocompleteCurrentPassword }?.value
                    if (username != null && password != null) pendingStore = username to password
                }
                latestOnSubmit()
                null
            }
            form
        },
        modifier = modifier.height(formHeight),
        update = { form ->
            fields.forEachIndexed { index, field ->
                val input = nodes.inputs.getOrNull(index) ?: return@forEachIndexed
                input.disabled = busy
                if (input.value != field.value && document.activeElement != input) input.value = field.value
                input.setAttribute("data-error", if (field.errorText != null) "true" else "false")
            }
            nodes.submit?.let { button ->
                button.disabled = !valid || busy
                button.textContent = submitLabel
            }
            nodes.secondary?.let { button -> button.disabled = busy }
            nodes.errorRow?.let { row -> row.textContent = error ?: "" }
            if (error != null) pendingStore = null
        },
        onRelease = { form ->
            nodes.inputs.forEach { it.oninput = null }
            form.onsubmit = null
            nodes.secondary?.onclick = null
            pendingStore?.let { (id, password) -> runCatching { browserStorePassword(id, password) } }
            pendingStore = null
        },
    )
}