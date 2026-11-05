package org.viptv.app.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned

// Attach the requester and ask for focus once its node is placed; requesting earlier drops the focus and logs a warning.
@Composable
internal fun FocusRequester.requestFocusWhenPlaced(enabled: Boolean = true): Modifier {
    var placed by remember(this) { mutableStateOf(false) }
    LaunchedEffect(this, placed, enabled) { if (placed && enabled) requestFocus() }
    return Modifier.focusRequester(this).onGloballyPositioned { placed = true }
}