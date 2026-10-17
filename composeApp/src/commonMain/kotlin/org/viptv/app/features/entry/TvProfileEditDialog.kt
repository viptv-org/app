package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.Profile

@Composable
internal fun TvProfileEditDialog(action: TvProfileEditAction, profile: Profile?, busy: Boolean, dismiss: () -> Unit,
    rename: (String, String) -> Unit, setPin: (String, String) -> Unit, delete: (String) -> Unit, pinAvailable: Boolean = true, editingCurrent: Boolean = true, openSettings: ((String) -> Unit)? = null) {
    if(action==TvProfileEditAction.Language||action==TvProfileEditAction.Maturity){
        ProfilePreferenceGuidance(action==TvProfileEditAction.Language,pinAvailable,editingCurrent,profile?.name,dismiss,openSettings)
        return
    }
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var currentPin by remember { mutableStateOf("") }; var newPin by remember { mutableStateOf("") }
    val editable = action in listOf(TvProfileEditAction.Name, TvProfileEditAction.Pin, TvProfileEditAction.Delete)
    val authorized = !profile.requiresLocalPin(pinAvailable) || currentPin.length in 4..8
    val valid = when (action) {
        TvProfileEditAction.Name -> name.isNotBlank() && name != profile?.name
        TvProfileEditAction.Pin -> newPin.length in 4..8 || (profile?.locked == true && newPin.isEmpty())
        else -> true
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text(when (action) {
        TvProfileEditAction.Name -> "Name"; TvProfileEditAction.Pin -> "Profile lock"
        TvProfileEditAction.Delete -> "Delete ${profile?.name}?"; TvProfileEditAction.Maturity -> "Maturity rating"
        TvProfileEditAction.Language -> "Language"
    }) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!editable) Text("This connection does not provide this profile preference yet.")
            if (action == TvProfileEditAction.Name) OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            if (editable && profile.requiresLocalPin(pinAvailable)) OutlinedTextField(currentPin,
                { if (it.length <= 8 && it.all(Char::isDigit)) currentPin = it }, label = { Text("Current PIN") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true)
            if (action == TvProfileEditAction.Pin) {
                OutlinedTextField(newPin, { if (it.length <= 8 && it.all(Char::isDigit)) newPin = it },
                    label = { Text(if (profile?.locked == true) "New PIN (blank to remove)" else "Set PIN") },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Text("A PIN keeps profiles separate in the app. The device owner can access local files.")
            }
            if (action == TvProfileEditAction.Delete) Text("This removes this profile’s history and add-on choices.")
        }
    }, confirmButton = {
        if (editable) TextButton(enabled = !busy && authorized && valid, onClick = {
            when (action) {
                TvProfileEditAction.Name -> rename(name, currentPin)
                TvProfileEditAction.Pin -> setPin(currentPin, newPin)
                TvProfileEditAction.Delete -> delete(currentPin)
                else -> Unit
            }
            dismiss()
        }) { Text(if (action == TvProfileEditAction.Delete) "Delete" else "Save") }
        else TextButton(onClick = dismiss) { Text("OK") }
    }, dismissButton = { if (editable) TextButton(onClick = dismiss) { Text("Cancel") } })
}
