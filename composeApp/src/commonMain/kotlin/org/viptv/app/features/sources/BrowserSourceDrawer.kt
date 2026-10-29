package org.viptv.app.features.sources

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal expect fun browserSourceDrawerProperties(): DialogProperties

// The layer's own back handling stays off: it would close the drawer on key down and leave the key up to the Details dialog, closing that too.
@Composable
internal fun BrowserSourceDrawer(back: () -> Unit, drawer: @Composable () -> Unit) {
    Dialog(onDismissRequest = back, properties = browserSourceDrawerProperties()) {
        var focused by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.key != Key.Escape) false else { if (event.type == KeyEventType.KeyUp) back(); true }
        }.onFocusChanged { focused = it.hasFocus }.focusGroup()) { drawer() }
        val focusManager = LocalFocusManager.current
        LaunchedEffect(Unit) {
            repeat(30) {
                withFrameNanos { }
                if (focused) return@LaunchedEffect
                focusManager.moveFocus(FocusDirection.Next)
            }
        }
    }
}
