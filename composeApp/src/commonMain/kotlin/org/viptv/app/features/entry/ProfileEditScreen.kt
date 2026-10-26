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
    profile: Profile?, count: Int, busy: Boolean, error: String?, compact: Boolean,
    back: () -> Unit, rename: (String, String) -> Unit, setPin: (String, String) -> Unit,
    setAvatar: (String, Int, String) -> Unit, delete: (String) -> Unit,
    television: Boolean = false, backChanged: ((() -> Unit)?) -> Unit = {}, pinAvailable: Boolean = true,
    editingCurrent: Boolean = true, openSettings: ((String) -> Unit)? = null, maturity: String? = null, language: String? = null,
) {
    if (television) {
        TvProfileEditScreen(profile, count, busy, error, back, rename, setPin, setAvatar, delete, backChanged = backChanged, pinAvailable = pinAvailable, editingCurrent = editingCurrent, openSettings = openSettings, maturity = maturity, language = language)
        return
    }
    if (compact) {
        MobileProfileEdit(profile, count, busy, error, back, rename, setPin, setAvatar, delete, pinAvailable = pinAvailable, editingCurrent = editingCurrent, openSettings = openSettings)
        return
    }
    var guidance by remember(profile?.id) { mutableStateOf<Boolean?>(null) }
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var oldPin by remember(profile?.id) { mutableStateOf("") }
    var newPin by remember(profile?.id) { mutableStateOf("") }
    var confirmingDelete by remember(profile?.id) { mutableStateOf(false) }
    var pickingAvatar by remember(profile?.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        Text("Edit profile", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        ProfileAvatar(profile, 88.dp, 18.dp)
        TextButton(onClick = { pickingAvatar = true }) { Text("Change avatar") }
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
        if (profile.requiresLocalPin(pinAvailable)) OutlinedTextField(oldPin, { if (it.length <= 8 && it.all(Char::isDigit)) oldPin = it },
            label = { Text("Current PIN") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        if (pinAvailable) { OutlinedTextField(newPin, { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it },
            label = { Text(if (profile?.locked == true) "New PIN (blank to remove)" else "Set PIN (optional)") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true)
        Text("A local PIN keeps profiles separate in the app. The device owner can access local files.", style = MaterialTheme.typography.bodySmall) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row {
            TextButton(onClick = back) { Text("Back") }
            Button(onClick = { rename(name, oldPin) }, enabled = !busy && name.isNotBlank() && name != profile?.name) { Text("Save name") }
        }
        if (pinAvailable) Button(onClick = { setPin(oldPin, newPin); oldPin = ""; newPin = "" },
            enabled = !busy && (newPin.length in 4..8 || (profile?.locked == true && newPin.isEmpty()))) {
            Text(if (profile?.locked == true && newPin.isEmpty()) "Remove PIN" else "Save PIN")
        }
        TextButton({ guidance = true }, enabled = !busy) { Text("Audio & subtitles") }
        TextButton({ guidance = false }, enabled = !busy) { Text("Maturity rating") }
        if (count > 1 && profile?.canDelete != false) TextButton(onClick = { confirmingDelete = true }, enabled = !busy) { Text("Delete profile") }
        }
    }
    if (confirmingDelete) AlertDialog(onDismissRequest = { confirmingDelete = false },
        title = { Text("Delete ${profile?.name}?") }, text = { Text("This removes this profile’s history and add-on choices.") },
        confirmButton = { TextButton(onClick = { delete(oldPin); confirmingDelete = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } })
    if (pickingAvatar) AvatarPicker(profile, busy, AvatarPickerLayout.Desktop, { pickingAvatar = false }, { pick, pin ->
        setAvatar(pick.style, pick.choice, pin); pickingAvatar = false
    }, pinAvailable = pinAvailable)
    guidance?.let { ProfilePreferenceGuidance(it, pinAvailable, editingCurrent, profile?.name, { guidance = null }, openSettings) }
}
