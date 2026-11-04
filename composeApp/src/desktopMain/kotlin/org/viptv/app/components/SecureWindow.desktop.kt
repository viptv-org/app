package org.viptv.app.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

internal object SecureWindowRecorder {
    var active: Int = 0
        private set
    var calls: Int = 0
        private set

    fun entered() {
        active++
        calls++
    }

    fun left() {
        active--
    }

    fun reset() {
        active = 0
        calls = 0
    }
}

@Composable
internal actual fun SecureWindow(secure: Boolean) {
    DisposableEffect(secure) {
        if (secure) SecureWindowRecorder.entered()
        onDispose { if (secure) SecureWindowRecorder.left() }
    }
}