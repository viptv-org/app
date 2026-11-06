package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.viptv.app.components.TvAction
import org.viptv.app.connection.Profile
import org.viptv.app.features.settings.TvDeleteProfileDialog
import org.viptv.app.features.settings.TvProfileNameDialog
import org.viptv.app.theme.Palette

@Composable
internal fun TvProfileEditDialog(action: TvProfileEditAction, profile: Profile?, busy: Boolean, dismiss: () -> Unit,
    rename: (String, String) -> Unit, setPin: (String, String) -> Unit, delete: (String) -> Unit, pinAvailable: Boolean = true,
    editingCurrent: Boolean = true, openSettings: ((String) -> Unit)? = null, scale: Float = 1f) {
    if(action==TvProfileEditAction.Language||action==TvProfileEditAction.Maturity){
        ProfilePreferenceGuidance(action==TvProfileEditAction.Language,pinAvailable,editingCurrent,profile?.name,dismiss,openSettings)
        return
    }
    if (action == TvProfileEditAction.Pin) { TvProfileLockDialog(profile, busy, scale, dismiss, setPin, pinAvailable); return }
    val pinRequired = profile.requiresLocalPin(pinAvailable)
    if (action == TvProfileEditAction.Name) {
        TvProfileNameDialog(profile?.name.orEmpty(), pinRequired, busy, null, dismiss,
            save = { name, pin -> rename(name, pin); dismiss() }, scale = scale)
        return
    }
    TvDeleteProfileDialog(profile?.name.orEmpty(), pinRequired, busy, null, dismiss,
        delete = { pin -> delete(pin); dismiss() }, scale = scale)
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
                    TvAction(Modifier.width(260.dp * scale).heightIn(min = 72.dp * scale), scale, requester = save,
                        enabled = !busy && (if (enteringCurrent) authorized else authorized && valid),
                        activate = { if (enteringCurrent) currentEntered = true else { setPin(currentPin, newPin); dismiss() } }) {
                        Text(if (enteringCurrent) "Continue" else "Save", color = it, fontSize = (26 * scale).sp,
                            lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                    TvAction(Modifier.width(220.dp * scale).heightIn(min = 72.dp * scale), scale, activate = dismiss) {
                        Text("Cancel", color = it, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
