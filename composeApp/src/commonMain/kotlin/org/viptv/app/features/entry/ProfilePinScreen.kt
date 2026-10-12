package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun ProfilePinScreen(name: String, busy: Boolean, error: String?, submit: (String) -> Unit, back: () -> Unit) {
    var pin by remember(name) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Enter $name’s PIN", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(pin, { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
            label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row { TextButton(onClick = back) { Text("Back") }; Button(onClick = { submit(pin); pin = "" }, enabled = !busy && pin.length in 4..8) { Text("Continue") } }
    }
}
