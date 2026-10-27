package org.viptv.app.features.entry

import com.composables.icons.lucide.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette

@Composable
internal fun MobileProfileEdit(
    profile: Profile?, count: Int, busy: Boolean, error: String?,
    back: () -> Unit, rename: (String, String) -> Unit, setPin: (String, String) -> Unit,
    setAvatar: (String, Int, String) -> Unit, delete: (String) -> Unit, pinAvailable: Boolean = true, editingCurrent: Boolean = true, openSettings: ((String) -> Unit)? = null,
) {
    var guidance by remember(profile?.id) { mutableStateOf<Boolean?>(null) }
    var pickingAvatar by remember(profile?.id) { mutableStateOf(false) }
    var editingName by remember(profile?.id) { mutableStateOf(false) }
    var editingPin by remember(profile?.id) { mutableStateOf(false) }
    var confirmingDelete by remember(profile?.id) { mutableStateOf(false) }
    org.viptv.app.components.TvFocusScroll(horizontal = false) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Lucide.ChevronLeft, "Back", Modifier.size(24.dp)) }
            Spacer(Modifier.weight(1f))
            Text("Edit profile", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = back) { Text("Done", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clickable(enabled = !busy) { pickingAvatar = true },
            horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(profile, 104.dp, 22.dp)
            Spacer(Modifier.height(12.dp))
            Text("Change avatar", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1D))) {
            EditSettingRow("Name", profile?.name.orEmpty(), onClick = { editingName = true })
            EditSettingRow("Audio & subtitles", "", onClick = { if (!busy) guidance = true })
            EditSettingRow("Maturity rating", "", onClick = { if (!busy) guidance = false })
            if (pinAvailable) HorizontalDivider(color = Color.White.copy(alpha = .08f))
            if (pinAvailable) EditSettingRow("Profile lock", if (profile?.locked == true) "On" else "Off",
                subtitle = "Ask for a PIN on this device", onClick = { editingPin = true })
        }
        error?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error) }
        if (count > 1 && profile?.canDelete != false) {
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1D))) {
                EditSettingRow("Delete profile", "", danger = true, onClick = { confirmingDelete = true })
            }
        }
    } }
    if (pickingAvatar) AvatarPicker(profile, busy, AvatarPickerLayout.Phone, { pickingAvatar = false }, { pick, pin ->
        setAvatar(pick.style, pick.choice, pin); pickingAvatar = false
    }, pinAvailable = pinAvailable)
    if (editingName) NameDialog(profile, busy, pinAvailable, { editingName = false }) { name, pin ->
        rename(name, pin); editingName = false
    }
    if (editingPin) PinDialog(profile, busy, { editingPin = false }) { old, next ->
        setPin(old, next); editingPin = false
    }
    guidance?.let { ProfilePreferenceGuidance(it, pinAvailable, editingCurrent, profile?.name, { guidance = null }, openSettings) }
    if (confirmingDelete) DeleteProfileDialog(profile, busy, pinAvailable, { confirmingDelete = false }) { pin ->
        delete(pin); confirmingDelete = false
    }
}

@Composable
private fun EditSettingRow(title: String, value: String, subtitle: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(if (subtitle == null) 52.dp else 58.dp)
        .clickable(onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(title, color = if (danger) Palette.danger else Palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, color = Palette.secondary, fontSize = 13.sp) }
        }
        Spacer(Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, color = Palette.secondary, fontSize = 14.sp)
        if (!danger) Text(" ›", color = Palette.secondary, fontSize = 20.sp)
    }
}

@Composable
private fun NameDialog(profile: Profile?, busy: Boolean, pinAvailable: Boolean, dismiss: () -> Unit, save: (String, String) -> Unit) {
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var pin by remember(profile?.id) { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Name") }, text = {
        Column {
            OutlinedTextField(name, { if (it.length <= 64) name = it }, singleLine = true)
            if (profile.requiresLocalPin(pinAvailable)) PinField(pin) { pin = it }
        }
    }, confirmButton = { TextButton(onClick = { save(name, pin) }, enabled = !busy && name.isNotBlank() && (!profile.requiresLocalPin(pinAvailable) || pin.length in 4..8)) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun PinDialog(profile: Profile?, busy: Boolean, dismiss: () -> Unit, save: (String, String) -> Unit) {
    var old by remember(profile?.id) { mutableStateOf("") }
    var next by remember(profile?.id) { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (profile?.locked == true) "Change profile PIN" else "Set profile PIN") }, text = {
        Column {
            if (profile?.locked == true) PinField(old) { old = it }
            PinField(next, if (profile?.locked == true) "New PIN (blank removes lock)" else "New PIN") { next = it }
        }
    }, confirmButton = { TextButton(onClick = { save(old, next) },
        enabled = !busy && (profile?.locked != true || old.length in 4..8) && (next.isEmpty() && profile?.locked == true || next.length in 4..8)) { Text("Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun DeleteProfileDialog(profile: Profile?, busy: Boolean, pinAvailable: Boolean, dismiss: () -> Unit, delete: (String) -> Unit) {
    var pin by remember(profile?.id) { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Delete ${profile?.name}?") },
        text = { Column {
            Text("This removes this profile’s history and add-on choices.")
            if (profile.requiresLocalPin(pinAvailable)) PinField(pin) { pin = it }
        } },
        confirmButton = { TextButton(onClick = { delete(pin) }, enabled = !busy && (!profile.requiresLocalPin(pinAvailable) || pin.length in 4..8)) { Text("Delete") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
private fun PinField(value: String, label: String = "Current PIN", change: (String) -> Unit) {
    OutlinedTextField(value, { if (it.length <= 8 && it.all(Char::isDigit)) change(it) },
        label = { Text(label) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
}
