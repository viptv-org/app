package org.viptv.app.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

internal object SecureWindowRecorder {
    const val activity = "activity"
    const val dialog = "dialog"

    private val holders = mutableMapOf<String, Int>()
    private val used = mutableSetOf<String>()

    fun entered(key: String) {
        holders[key] = (holders[key] ?: 0) + 1
        used += key
    }

    fun left(key: String) {
        val remaining = (holders[key] ?: 1) - 1
        if (remaining <= 0) holders.remove(key) else holders[key] = remaining
    }

    fun active(key: String): Int = if ((holders[key] ?: 0) > 0) 1 else 0
    fun everUsed(key: String): Boolean = key in used
    fun reset() {
        holders.clear()
        used.clear()
    }
}

@Composable
internal actual fun SecureWindow(secure: Boolean, dialog: Boolean) {
    val key = if (dialog) SecureWindowRecorder.dialog else SecureWindowRecorder.activity
    DisposableEffect(key, secure) {
        if (secure) SecureWindowRecorder.entered(key)
        onDispose { if (secure) SecureWindowRecorder.left(key) }
    }
}