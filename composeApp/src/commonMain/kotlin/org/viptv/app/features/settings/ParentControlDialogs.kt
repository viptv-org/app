package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.viptv.app.connection.*
import org.viptv.app.theme.Palette
import org.viptv.app.components.TextButton
import org.viptv.app.components.Switch

@Composable
internal fun ParentPinDialog(mode: String, configured: Boolean, busy: Boolean, failure: String?, dismiss: () -> Unit,
    submit: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    val needsCurrent = configured || mode != "save"
    val changesPin = mode == "save"
    val mismatch = confirmation.isNotEmpty() && next != confirmation
    val valid = (!needsCurrent || validParentPin(current)) && (!changesPin || validParentPin(next) && next == confirmation)
    LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() },
        title = { Text(when (mode) { "unlock" -> "Unlock parental controls"; "remove" -> "Remove parent PIN?"; else -> if (configured) "Change parent PIN" else "Set parent PIN" }) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (mode == "remove") Text("The household PIN will be removed. Kids restrictions must be off for every profile.")
            if (needsCurrent) ParentPinField(if (mode == "unlock") "Parent PIN" else "Current PIN", current, { current = it }, !busy, Modifier.focusRequester(first))
            if (changesPin) {
                ParentPinField("New PIN", next, { next = it }, !busy, if (needsCurrent) Modifier else Modifier.focusRequester(first))
                ParentPinField("Confirm new PIN", confirmation, { confirmation = it }, !busy,
                    if (mismatch) Modifier.semantics { error("PINs do not match") } else Modifier, mismatch)
                Text(if (mismatch) "PINs do not match." else "Use 4 to 8 digits. Keep your parent PIN private.", color = if (mismatch) Palette.danger else Palette.secondary)
            }
            failure?.let { Text(it, color = Palette.danger) }
        } },
        confirmButton = { TextButton({ if (!busy && valid) submit(current, if (changesPin) next else "") }, enabled = !busy && valid) {
            Text(when (mode) { "unlock" -> "Unlock"; "remove" -> "Remove PIN"; else -> "Save PIN" })
        } },
        dismissButton = { TextButton(dismiss, enabled = !busy) { Text("Cancel") } })
}

@Composable
private fun ParentPinField(label: String, value: String, change: (String) -> Unit, enabled: Boolean,
    modifier: Modifier = Modifier, invalid: Boolean = false) {
    OutlinedTextField(value, { change(it.filter { c -> c in '0'..'9' }.take(8)) }, modifier.fillMaxWidth(),
        enabled = enabled, label = { Text(label) }, singleLine = true, isError = invalid,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
}

@Composable
internal fun KidsPolicyDialog(profile: ServerProfile, pinConfigured: Boolean, busy: Boolean, failure: String?,
    dismiss: () -> Unit, save: (KidsPolicy) -> Unit) {
    var enabled by remember(profile.id) { mutableStateOf(profile.kids) }
    var age by remember(profile.id) { mutableStateOf(profile.max_age?.toString().orEmpty()) }
    val parsed = age.toIntOrNull()
    val valid = parsed != null && parsed in 0..17 && (!enabled || pinConfigured)
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Restrictions for ${profile.name}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Kids restrictions")
                Switch(enabled, { enabled = it }, enabled = !busy && pinConfigured)
            }
            if (!pinConfigured) Text("Set a parent PIN before enabling kids restrictions.", color = Palette.secondary)
            OutlinedTextField(age, { age = it.filter { c -> c in '0'..'9' }.take(2) }, label = { Text("Maximum age") },
                enabled = !busy && enabled, singleLine = true, isError = enabled && (parsed == null || parsed !in 0..17),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text("Only titles approved for this age range are available. Unrated titles may need parent approval.", color = Palette.secondary)
            failure?.let { Text(it, color = Palette.danger) }
        } }, confirmButton = { TextButton({ if (valid && !busy) save(KidsPolicy(enabled, requireNotNull(parsed))) }, enabled = valid && !busy) { Text("Save restrictions") } },
        dismissButton = { TextButton(dismiss, enabled = !busy) { Text("Cancel") } })
}
