package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.viptv.app.components.Brand
import org.viptv.app.connection.RecoveryKit
import org.viptv.app.theme.Palette

@Composable
internal fun RecoveryCodesScreen(kit: RecoveryKit?, busy: Boolean, error: String?, proceed: () -> Unit, signIn: () -> Unit) {
    var saved by remember(kit) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Brand()
            Text("Save your recovery code", style = MaterialTheme.typography.headlineLarge)
            Text(if (kit?.recovered == true) "Your password is updated and your previous recovery code no longer works. Save the replacement below."
                else "Your account is created. Keep this code somewhere private so you can recover access if you lose your password.", color = Palette.secondary)
            kit?.codes?.forEach { code ->
                Surface(color = Palette.surface, shape = MaterialTheme.shapes.medium) {
                    SelectionContainer { Text(code, Modifier.fillMaxWidth().padding(20.dp), fontFamily = FontFamily.Monospace) }
                }
            }
            Text("This code is shown only now. VIPTV does not save a copy on this device.", color = Palette.secondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(saved, { saved = it }, enabled = !busy)
                Text("I saved my recovery code")
            }
            error?.let { Text(it, color = Palette.danger) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(proceed, enabled = saved && kit != null && !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (error == null) "Continue" else "Retry sign-in")
            }
            if (error != null) TextButton(signIn, enabled = saved && !busy, modifier = Modifier.fillMaxWidth()) { Text("Return to sign in") }
        }
    }
}
