package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette
import org.viptv.app.components.Button

@Composable
internal fun AddonInstallForm(address: TextFieldState, busy: Boolean, install: () -> Unit, compact: Boolean = false, dismiss: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (compact) "Add-on URL" else "Add from a link", fontSize = 14.sp)
        val field: @Composable (Modifier) -> Unit = { modifier ->
            AddonUrlField(address, !busy, modifier, dismiss)
        }
        val action: @Composable (Modifier) -> Unit = { modifier ->
            Button(onClick = install, enabled = !busy && address.text.isNotBlank(), modifier = modifier.height(50.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Palette.text, contentColor = Palette.background)) { Text("Install") }
        }
        if (compact) { field(Modifier.fillMaxWidth()); action(Modifier.fillMaxWidth()) }
        else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { field(Modifier.weight(1f)); action(Modifier) }
        Text("Only install add-ons you trust. You can remove one at any time.", color = Palette.secondary, fontSize = 13.sp)
    }
}
