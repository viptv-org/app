package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.Profile

@Composable
fun ProfileEditScreen(
    profile: Profile?, count: Int, busy: Boolean, error: String?,
    back: () -> Unit, rename: (String, String) -> Unit, setPin: (String, String) -> Unit, delete: (String) -> Unit,
) {
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var oldPin by remember(profile?.id) { mutableStateOf("") }
    var newPin by remember(profile?.id) { mutableStateOf("") }
    var confirmingDelete by remember(profile?.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Edit profile", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
        if (profile?.locked == true) OutlinedTextField(oldPin, { if (it.length <= 8 && it.all(Char::isDigit)) oldPin = it },
            label = { Text("Current PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        OutlinedTextField(newPin, { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it },
            label = { Text(if (profile?.locked == true) "New PIN (blank to remove)" else "Set PIN (optional)") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Text("A local PIN keeps profiles separate in the app. The device owner can access local files.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row {
            TextButton(onClick = back) { Text("Back") }
            Button(onClick = { rename(name, oldPin) }, enabled = !busy && name.isNotBlank() && name != profile?.name) { Text("Save name") }
        }
        Button(onClick = { setPin(oldPin, newPin); oldPin = ""; newPin = "" },
            enabled = !busy && (newPin.length in 4..8 || (profile?.locked == true && newPin.isEmpty()))) {
            Text(if (profile?.locked == true && newPin.isEmpty()) "Remove PIN" else "Save PIN")
        }
        if (count > 1) TextButton(onClick = { confirmingDelete = true }, enabled = !busy) { Text("Delete profile") }
    }
    if (confirmingDelete) AlertDialog(onDismissRequest = { confirmingDelete = false },
        title = { Text("Delete ${profile?.name}?") }, text = { Text("This removes this profile’s history and add-on choices from this device.") },
        confirmButton = { TextButton(onClick = { delete(oldPin); confirmingDelete = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } })
}
