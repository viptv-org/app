package org.viptv.app.features.sources

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The web source drawer over the browser Details dialog (app#60). That dialog is a
 * window layer floating above the page, so the drawer needs a layer of its own on top
 * to stay visible; the dialog underneath stays composed with its season and scroll.
 * The layer takes the pointer (the drawer's scrim closes it) and keyboard focus, which
 * starts in the drawer. Escape closes only the drawer: it acts on key up like the
 * dialog's own Escape, and the key down is kept from the layers underneath.
 */
@Composable
internal fun BrowserSourceDrawer(back: () -> Unit, drawer: @Composable () -> Unit) {
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = back, properties = DialogProperties(usePlatformDefaultWidth = false, scrimColor = Color.Transparent)) {
        Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.key != Key.Escape) false else { if (event.type == KeyEventType.KeyUp) back(); true }
        }.focusRequester(focus).focusGroup()) { drawer() }
        val focusManager = LocalFocusManager.current
        // The first focusable in the drawer (its Close button: the scrim takes no focus).
        LaunchedEffect(Unit) { if (!focus.requestFocus(FocusDirection.Enter)) focusManager.moveFocus(FocusDirection.Next) }
    }
}
