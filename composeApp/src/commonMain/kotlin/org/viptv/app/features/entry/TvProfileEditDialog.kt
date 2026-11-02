package org.viptv.app.features.entry

import org.viptv.app.components.AppTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.viptv.app.components.TvAction
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette
import org.viptv.app.components.TextButton

@Composable
internal fun TvProfileEditDialog(action: TvProfileEditAction, profile: Profile?, busy: Boolean, dismiss: () -> Unit,
    rename: (String, String) -> Unit, setPin: (String, String) -> Unit, delete: (String) -> Unit, pinAvailable: Boolean = true,
    editingCurrent: Boolean = true, openSettings: ((String) -> Unit)? = null, scale: Float = 1f) {
    if(action==TvProfileEditAction.Language||action==TvProfileEditAction.Maturity){
        ProfilePreferenceGuidance(action==TvProfileEditAction.Language,pinAvailable,editingCurrent,profile?.name,dismiss,openSettings)
        return
    }
    if (action == TvProfileEditAction.Pin) { TvProfileLockDialog(profile, busy, scale, dismiss, setPin, pinAvailable); return }
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var currentPin by remember { mutableStateOf("") }
    val editable = action in listOf(TvProfileEditAction.Name, TvProfileEditAction.Delete)
    val authorized = !profile.requiresLocalPin(pinAvailable) || currentPin.length in 4..8
    val valid = when (action) {
        TvProfileEditAction.Name -> name.isNotBlank() && name != profile?.name
        else -> true
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text(when (action) {
        TvProfileEditAction.Name -> "Name"; TvProfileEditAction.Pin -> "Profile lock"
        TvProfileEditAction.Delete -> "Delete ${profile?.name}?"; TvProfileEditAction.Maturity -> "Maturity rating"
        TvProfileEditAction.Language -> "Language"
    }) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!editable) Text("This connection does not provide this profile preference yet.")
            if (action == TvProfileEditAction.Name) AppTextField(name, { name = it }, label = "Name")
            if (editable && profile.requiresLocalPin(pinAvailable)) AppTextField(currentPin,
                { if (it.length <= 8 && it.all(Char::isDigit)) currentPin = it }, label = "Current PIN",
                visualTransformation = PasswordVisualTransformation())
            if (action == TvProfileEditAction.Delete) Text("This removes this profile’s history and add-on choices.")
        }
    }, confirmButton = {
        if (editable) TextButton(enabled = !busy && authorized && valid, onClick = {
            when (action) {
                TvProfileEditAction.Name -> rename(name, currentPin)
                TvProfileEditAction.Delete -> delete(currentPin)
                else -> Unit
            }
            dismiss()
        }) { Text(if (action == TvProfileEditAction.Delete) "Delete" else "Save") }
        else TextButton(onClick = dismiss) { Text("OK") }
    }, dismissButton = { if (editable) TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
internal fun TvProfileLockDialog(profile: Profile?, busy: Boolean, scale: Float, dismiss: () -> Unit,
    setPin: (String, String) -> Unit, pinAvailable: Boolean = true) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var currentEntered by remember { mutableStateOf(false) }
    val authorized = !profile.requiresLocalPin(pinAvailable) || currentPin.length in 4..8
    val valid = newPin.length in 4..8 || (profile?.locked == true && newPin.isEmpty())
    val enteringCurrent = profile.requiresLocalPin(pinAvailable) && !currentEntered
    val pin = if (enteringCurrent) currentPin else newPin
    val first = remember { FocusRequester() }
    val save = remember { FocusRequester() }
    LaunchedEffect(enteringCurrent) {
        repeat(60) {
            if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    val type: (String) -> Unit = { digit ->
        if (enteringCurrent) { if (currentPin.length < 8) currentPin += digit } else if (newPin.length < 8) newPin += digit
    }
    val erase: () -> Unit = { if (enteringCurrent) currentPin = currentPin.dropLast(1) else newPin = newPin.dropLast(1) }
    Popup(onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .75f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(800.dp * scale).clip(RoundedCornerShape(24.dp * scale)).background(Color(0xFF202023))
                .padding(horizontal = 48.dp * scale, vertical = 44.dp * scale), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (enteringCurrent) "Enter the current PIN" else "Profile lock", Modifier.fillMaxWidth(),
                    fontSize = (44 * scale).sp, lineHeight = (50 * scale).sp, letterSpacing = (-.88f * scale).sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp * scale))
                Text(if (enteringCurrent) "This profile is locked. Enter its current PIN to change the lock."
                    else if (profile?.locked == true) "Enter a new PIN, or save it empty to remove the lock."
                    else "Ask for a PIN before this profile opens.",
                    Modifier.fillMaxWidth(), fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp, color = Palette.secondary)
                Spacer(Modifier.height(32.dp * scale))
                TvPinDigits(pin, scale)
                Spacer(Modifier.height(36.dp * scale))
                TvPinKeys(pin, scale, busy, first, type, erase, submit = {
                    if (enteringCurrent) {
                        if (authorized) { currentEntered = true; runCatching { save.requestFocus() } }
                    } else { runCatching { save.requestFocus() } }
                })
                Spacer(Modifier.height(36.dp * scale))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp * scale)) {
                    TvAction(Modifier.width(260.dp * scale).height(72.dp * scale), scale, requester = save,
                        enabled = !busy && (if (enteringCurrent) authorized else authorized && valid),
                        activate = { if (enteringCurrent) currentEntered = true else { setPin(currentPin, newPin); dismiss() } }) {
                        Text(if (enteringCurrent) "Continue" else "Save", color = it, fontSize = (26 * scale).sp,
                            lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                    TvAction(Modifier.width(220.dp * scale).height(72.dp * scale), scale, activate = dismiss) {
                        Text("Cancel", color = it, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
