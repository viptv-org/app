package org.viptv.app.features.browse

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TvAction
import org.viptv.engine.wire.QueryControl

@Composable
internal fun DiscoveryMultiChoiceFilter(control: QueryControl, selected: List<String>, modifier: Modifier,
    television: Boolean, scale: Float, changed: (List<String>) -> Unit) {
    var expanded by remember(control) { mutableStateOf(false) }
    var restore by remember { mutableStateOf(false) }
    val trigger = remember { FocusRequester() }
    LaunchedEffect(restore) {
        if (restore) { withFrameNanos { }; trigger.requestFocus(); restore = false }
    }
    val label = if (selected.isEmpty()) control.label else "${control.label} (${selected.size})"
    val target = modifier.focusRequester(trigger)
    if (television) TvAction(target.height(56.dp * scale), scale, activate = { expanded = true },
        cornerRadius = 28.dp * scale, contentPadding = PaddingValues(horizontal = 24.dp * scale)) {
        Text(label, color = it, fontSize = (24 * scale).sp)
    } else OutlinedButton(onClick = { expanded = true }, modifier = target,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = org.viptv.app.theme.Palette.text)) { Text(label) }
    if (expanded) DiscoveryMultiChoiceSheet(control, selected, television, scale,
        close = { expanded = false; restore = true }, apply = changed)
}
