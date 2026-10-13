package org.viptv.app.components

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager

/** Run after the target is composed; a newer focus destination cancels the request. */
@Composable
internal fun TvRequestedFocus(requester: FocusRequester?, epoch: Any?, enabled: Boolean = true) {
    val input = LocalInputModeManager.current
    LaunchedEffect(epoch, enabled) {
        if (enabled && requester != null) {
            input.requestInputMode(InputMode.Keyboard)
            if (!requester.requestFocus()) { withFrameNanos { }; requester.requestFocus() }
        }
    }
}
