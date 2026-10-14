package org.viptv.app.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun AccountSignOutButton(label: String, busy: Boolean, compact: Boolean, click: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides if (compact) 44.dp else 0.dp) {
        OutlinedButton(click, Modifier.height(if (compact) 40.dp else 32.dp).onFocusChanged { focused = it.isFocused }, enabled = !busy,
            contentPadding = PaddingValues(horizontal = 15.dp), shape = RoundedCornerShape(10.dp),
            border = BorderStroke(if (focused) 2.dp else 1.dp, Palette.text.copy(alpha = if (focused) .9f else .14f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun AccountDeviceConfirmation(name: String?, all: Boolean, busy: Boolean, error: String?, dismiss: () -> Unit, confirm: () -> Unit) {
    var submitting by remember { mutableStateOf(false) }
    LaunchedEffect(busy, error) { if (!busy && error != null) submitting = false }
    val locked = busy || submitting
    AlertDialog(onDismissRequest = { if (!busy && !submitting) dismiss() }, containerColor = Palette.surface,
        title = { Text(if (all) "Sign out of all devices?" else "Sign out $name?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (all) "Every device, including this browser, will need to sign in again. Your profiles, lists and history stay on your account."
                else "This device will need to sign in again. Your profiles, lists and history stay on your account.")
            error?.let { Text(it, color = Palette.danger) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton({ if (!busy && !submitting) { submitting = true; confirm() } }, enabled = !locked) { Text(if (all) "Sign out of all devices" else "Sign out", color = Palette.danger) } },
        dismissButton = { TextButton({ if (!busy && !submitting) dismiss() }, enabled = !locked) { Text("Cancel") } })
}
