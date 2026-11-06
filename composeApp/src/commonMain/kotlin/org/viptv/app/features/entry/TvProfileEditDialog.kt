package org.viptv.app.features.entry

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.Profile
import org.viptv.app.features.settings.TvDeleteProfileDialog
import org.viptv.app.features.settings.TvDialogAction
import org.viptv.app.features.settings.TvDialogField
import org.viptv.app.features.settings.TvDialogPanel
import org.viptv.app.features.settings.TvProfileNameDialog
import org.viptv.app.features.settings.pinDigits
import org.viptv.app.features.settings.rememberTvDialogStops
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
    val locked = profile?.locked == true
    val needsCurrent = profile.requiresLocalPin(pinAvailable)
    val enteringCurrent = needsCurrent && !currentEntered
    val removing = locked && newPin.isEmpty()
    val valid = newPin.length in 4..8 || removing
    val stops = rememberTvDialogStops(4)
    val currentStop = stops[0]
    val newStop = stops[1]
    val actionStop = stops[2]
    val cancelStop = stops[3]
    val stageStops = if (enteringCurrent) listOf(currentStop, actionStop, cancelStop) else listOf(newStop, actionStop, cancelStop)
    val close = { if (!busy) dismiss() }
    TvDialogPanel(scale, if (enteringCurrent) "Enter the current PIN" else "Profile lock", stageStops,
        if (enteringCurrent) currentStop else newStop, close, actions = {
            TvDialogAction(scale, if (enteringCurrent) "Continue" else if (removing) "Remove PIN" else "Save", actionStop,
                enabled = if (enteringCurrent) !busy && currentPin.length in 4..8 else !busy && valid) {
                if (enteringCurrent) currentEntered = true
                else { setPin(currentPin, newPin); dismiss() }
            }
            TvDialogAction(scale, "Cancel", cancelStop, enabled = !busy) { dismiss() }
        }) {
        Text(if (enteringCurrent) "This profile is locked. Enter its current PIN to change the lock."
            else if (locked) "Enter a new PIN, or save it empty to remove the lock."
            else "Ask for a PIN before this profile opens.",
            color = Palette.secondary, fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp)
        Spacer(Modifier.height((24 * scale).dp))
        if (enteringCurrent) {
            TvDialogField("Current PIN", currentPin, scale, !busy, currentStop, secret = true, onEscape = close) {
                currentPin = pinDigits(it)
            }
        } else {
            TvDialogField("New PIN", newPin, scale, !busy, newStop, secret = true, onEscape = close) { newPin = pinDigits(it) }
        }
    }
}
