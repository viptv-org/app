package org.viptv.app.features.settings

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun AddonUrlField(state: TextFieldState, enabled: Boolean, modifier: Modifier, dismiss: (() -> Unit)?) {
    OutlinedTextField(state = state, modifier = modifier.onPreviewKeyEvent {
        if (it.key == Key.Escape && it.type == KeyEventType.KeyUp && dismiss != null) { dismiss(); true } else false
    }, enabled = enabled, lineLimits = TextFieldLineLimits.SingleLine,
        label = { Text("Add-on URL") }, placeholder = { Text("https://addon.example/manifest.json") }, shape = RoundedCornerShape(12.dp))
}
