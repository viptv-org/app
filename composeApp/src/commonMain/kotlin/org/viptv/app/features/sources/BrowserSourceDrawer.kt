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

/**
 * The drawer layer's properties: no back handling and full width. Desktop and web also
 * clear the layer's own scrim, since the drawer draws its own; Android's
 * DialogProperties has no scrim colour (app#88).
 */
internal expect fun browserSourceDrawerProperties(): DialogProperties

/**
 * The web source drawer over the browser Details dialog (app#60). That dialog is a
 * window layer floating above the page, so the drawer needs a layer of its own on top
 * to stay visible; the dialog underneath stays composed with its season and scroll.
 * The layer takes the pointer (the drawer's scrim closes it) and keyboard focus, which
 * starts in the drawer. Escape closes only the drawer: it acts on key up like the
 * dialog's own Escape, and the key down is kept from the layers underneath. The layer's
 * own back handling stays off, since it would close the drawer on key down and leave
 * the key up to the Details dialog, closing that too.
 */
@Composable
internal fun BrowserSourceDrawer(back: () -> Unit, drawer: @Composable () -> Unit) {
    Dialog(onDismissRequest = back, properties = browserSourceDrawerProperties()) {
        var focused by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.key != Key.Escape) false else { if (event.type == KeyEventType.KeyUp) back(); true }
        }.onFocusChanged { focused = it.hasFocus }.focusGroup()) { drawer() }
        val focusManager = LocalFocusManager.current
        // Focus starts on the drawer's first focusable, its Close button (the scrim takes
        // none). The layer can still be settling for a few frames after it opens.
        LaunchedEffect(Unit) {
            repeat(30) {
                withFrameNanos { }
                if (focused) return@LaunchedEffect
                focusManager.moveFocus(FocusDirection.Next)
            }
        }
    }
}
