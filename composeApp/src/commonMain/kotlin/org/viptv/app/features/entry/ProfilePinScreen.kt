package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.Profile
import org.viptv.app.components.Button
import org.viptv.app.components.TextButton

@Composable
fun ProfilePinScreen(profile: Profile?, compact: Boolean, busy: Boolean, error: String?, submit: (String) -> Unit, back: () -> Unit, television: Boolean = false) {
    var pin by remember(profile?.id) { mutableStateOf("") }
    if (television) {
        TvPinScreen(profile, pin, busy, error, { if (pin.length < 8) pin += it }, { pin = pin.dropLast(1) },
            { if (pin.length in 4..8) { submit(pin); pin = "" } }, back)
        return
    }
    if (compact) {
        MobilePinScreen(profile, pin, busy, error, { digit -> if (pin.length < 8) pin += digit },
            { pin = pin.dropLast(1) }, { if (pin.length in 4..8) { submit(pin); pin = "" } }, back)
        return
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (profile?.lockKind == "parent") "Enter the account parent PIN" else "Enter ${profile?.name.orEmpty()}’s PIN", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(pin, { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
            label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row { TextButton(onClick = back) { Text("Back") }; Button(onClick = { submit(pin); pin = "" }, enabled = !busy && pin.length in 4..8) { Text("Continue") } }
    }
}
