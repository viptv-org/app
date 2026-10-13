package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

@Composable
internal fun SettingsLeaveDialog(local: Boolean, compact: Boolean, busy: Boolean, error: String?, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, containerColor = Palette.surface, shape = RoundedCornerShape(24.dp),
        title = { Text(if (local) "Leave local mode on this device?" else if (compact) "Sign out of VIPTV on this phone?" else "Sign out of VIPTV on this device?",
            fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (local) "Your profiles and progress stay on this device." else "Your profiles and progress stay on your account.")
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = Palette.danger) }
        } },
        confirmButton = { TextButton(onClick = confirm, enabled = !busy) { Text(if (local) "Leave" else "Sign out", color = Palette.danger) } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") } })
}
