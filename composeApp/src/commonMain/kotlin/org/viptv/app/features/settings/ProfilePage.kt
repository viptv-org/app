package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.Switch
import org.viptv.app.components.TextButton
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.pointerSelectable
import org.viptv.app.components.pointerToggleable
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.AvatarPick
import org.viptv.app.features.entry.AvatarPicker
import org.viptv.app.features.entry.AvatarPickerLayout
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.features.entry.requiresLocalPin
import org.viptv.app.theme.Palette

internal data class ProfilePageValues(
    val profile: Profile,
    val local: Boolean,
    val kids: Boolean = false,
    val maxAge: Int? = null,
    val approvedTitles: Int? = null,
    val householdPinConfigured: Boolean = false,
    val householdUnlocked: Boolean = false,
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val canDelete: Boolean = true,
) {
    val pinRequired get() = profile.requiresLocalPin(local)
    val householdPinRequired get() = householdPinConfigured && !householdUnlocked
}

internal data class ProfilePageActions(
    val rename: (String, String) -> Unit = { _, _ -> },
    val avatar: (AvatarPick, String) -> Unit = { _, _ -> },
    val kids: (Boolean, String) -> Unit = { _, _ -> },
    val maturity: (Int, String) -> Unit = { _, _ -> },
    val approvedTitles: () -> Unit = {},
    val lock: (String, String) -> Unit = { _, _ -> },
    val parentalControls: () -> Unit = {},
    val language: () -> Unit = {},
    val delete: (String) -> Unit = {},
)

internal data class MaturityLimit(val age: Int, val label: String)

internal val maturityLimits = listOf(MaturityLimit(0, "G · TV-Y"), MaturityLimit(7, "TV-Y7"), MaturityLimit(10, "PG · TV-PG"),
    MaturityLimit(13, "PG-13"), MaturityLimit(14, "TV-14"), MaturityLimit(17, "R"))

internal fun maturityLimitValue(values: ProfilePageValues): String = when {
    !values.kids -> "All ratings"
    else -> maturityLimits.firstOrNull { it.age == values.maxAge }?.label
        ?: values.maxAge?.let { "Up to age $it" } ?: "All ratings"
}

internal fun languageSummary(audio: String?, subtitle: String?): String {
    val parts = listOfNotNull(audio?.takeIf(String::isNotBlank), subtitle?.takeIf(String::isNotBlank))
    return if (parts.isEmpty()) "Original language" else parts.joinToString(" · ")
}

internal class ProfileDialogs {
    var name by mutableStateOf(false)
    var avatar by mutableStateOf(false)
    var lock by mutableStateOf(false)
    var maturity by mutableStateOf(false)
    var delete by mutableStateOf(false)
    var kidsPin by mutableStateOf(false)
    var maturityPin by mutableStateOf<Int?>(null)

    fun close() {
        name = false; avatar = false; lock = false; maturity = false; delete = false
        kidsPin = false; maturityPin = null
    }

    fun toggleKids(values: ProfilePageValues, actions: ProfilePageActions) {
        when {
            values.kids -> actions.kids(false, "")
            values.householdPinRequired -> kidsPin = true
            else -> actions.kids(true, "")
        }
    }

    fun chooseMaturity(age: Int, values: ProfilePageValues, actions: ProfilePageActions) {
        maturity = false
        if (values.householdPinRequired) maturityPin = age else actions.maturity(age, "")
    }
}

@Composable
internal fun rememberProfileDialogs(profileId: String, saved: Long): ProfileDialogs {
    val dialogs = remember(profileId) { ProfileDialogs() }
    val seen = remember(profileId) { mutableStateOf(saved) }
    SideEffect {
        if (seen.value != saved) {
            seen.value = saved
            dialogs.close()
        }
    }
    return dialogs
}

@Composable
internal fun ProfileSettingsPage(values: ProfilePageValues?, busy: Boolean, error: String?, saved: Long,
    actions: ProfilePageActions, layout: SettingsLayout) {
    val dialogs = rememberProfileDialogs(values?.profile?.id.orEmpty(), saved)
    if (values == null) {
        Text("Choose a profile to edit it.", Modifier.padding(32.dp), color = Palette.secondary)
        return
    }
    val horizontal = if (layout == SettingsLayout.Phone) 16.dp else 32.dp
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = horizontal, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profile", Modifier.padding(top = 16.dp), fontSize = if (layout == SettingsLayout.Phone) 22.sp else 26.sp,
            fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(14.dp), color = Palette.surface) {
            Column {
                ProfileRow("Name", value = values.profile.name, enabled = !busy) { dialogs.name = true }
                SettingsDividerLine()
                ProfileRow("Avatar", leading = { ProfileAvatar(values.profile, 32.dp, 10.dp) }, enabled = !busy) { dialogs.avatar = true }
                if (!values.local) {
                    SettingsDividerLine()
                    ProfileRow("Kids profile", note = "Limits titles, search and Live for this profile.", checked = values.kids,
                        enabled = !busy) { dialogs.toggleKids(values, actions) }
                    SettingsDividerLine()
                    ProfileRow("Maturity limit", note = "Titles above this rating are hidden for this profile",
                        value = maturityLimitValue(values), enabled = !busy) { dialogs.maturity = true }
                    if (values.kids) {
                        SettingsDividerLine()
                        ProfileRow("Approved titles", note = "Only these titles show in the kids library",
                            value = values.approvedTitles?.toString() ?: "—",
                            enabled = !busy) { actions.approvedTitles() }
                    }
                }
                SettingsDividerLine()
                if (values.local) {
                    ProfileRow("Profile lock", note = "Ask for a PIN before this profile opens",
                        checked = values.profile.locked, enabled = !busy) { dialogs.lock = true }
                } else {
                    ProfileRow("Profile lock", note = "Ask for a PIN before this profile opens", enabled = !busy) {
                        actions.parentalControls()
                    }
                }
                SettingsDividerLine()
                ProfileRow("Language", note = "Default audio and subtitles for this profile",
                    value = languageSummary(values.audioLanguage, values.subtitleLanguage), enabled = !busy) { actions.language() }
            }
        }
        error?.let { Text(it, Modifier.padding(top = 8.dp), color = Palette.danger) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (values.canDelete) {
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = Palette.surface) {
                ProfileRow("Delete profile", danger = true, enabled = !busy) { dialogs.delete = true }
            }
        }
    }
    ProfileDialogsContent(dialogs, values, busy, error, actions,
        if (layout == SettingsLayout.Phone) AvatarPickerLayout.Phone else AvatarPickerLayout.Desktop)
}

@Composable
internal fun ProfileDialogsContent(dialogs: ProfileDialogs, values: ProfilePageValues, busy: Boolean, error: String?,
    actions: ProfilePageActions, avatarLayout: AvatarPickerLayout) {
    if (dialogs.name) ProfileNameDialog(values.profile.name, values.pinRequired, busy, error,
        { if (!busy) dialogs.name = false }) { name, pin -> actions.rename(name, pin) }
    if (dialogs.avatar) AvatarPicker(values.profile, busy, avatarLayout, { if (!busy) dialogs.avatar = false },
        choose = { pick, pin -> actions.avatar(pick, pin) })
    if (dialogs.lock) ProfileLockDialog(values.profile.locked, values.pinRequired, busy, error,
        { if (!busy) dialogs.lock = false }) { old, next -> actions.lock(old, next) }
    if (dialogs.maturity) MaturityLimitDialog(values.maxAge.takeIf { values.kids }, busy, error,
        { if (!busy) dialogs.maturity = false }) { age -> dialogs.chooseMaturity(age, values, actions) }
    if (dialogs.delete) DeleteProfileDialog(values.profile.name, values.pinRequired, busy, error,
        { if (!busy) dialogs.delete = false }) { pin -> actions.delete(pin) }
    if (dialogs.kidsPin) HouseholdPinDialog("Turn on Kids profile", busy, error, { if (!busy) dialogs.kidsPin = false }) { pin ->
        actions.kids(true, pin)
    }
    dialogs.maturityPin?.let { age -> HouseholdPinDialog("Change the maturity limit", busy, error,
        { if (!busy) dialogs.maturityPin = null }) { pin -> actions.maturity(age, pin) } }
}

@Composable
private fun SettingsDividerLine() = HorizontalDivider(color = Palette.raised, thickness = 1.dp)

@Composable
internal fun ProfileRow(label: String, note: String? = null, value: String? = null, checked: Boolean? = null,
    danger: Boolean = false, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null, action: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val affordance = if (checked != null)
        Modifier.pointerToggleable(checked, enabled = enabled, role = Role.Switch, shape = shape, onValueChange = { action() })
    else Modifier.pointerClickable(enabled = enabled, shape = shape, onClick = action)
    Row(Modifier.fillMaxWidth().then(affordance).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
                color = if (danger) Palette.danger else Palette.text)
            note?.let { Text(it, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = Palette.secondary) }
        }
        if (checked != null) Switch(checked, null)
        if (value != null) Text(value, color = Palette.secondary, fontSize = 14.sp)
        if (!danger && checked == null) Icon(Lucide.ChevronRight, null, Modifier.size(17.dp), tint = Palette.tertiary)
    }
}

@Composable
internal fun ProfileNameDialog(name: String, pinRequired: Boolean, busy: Boolean, failure: String?, dismiss: () -> Unit,
    save: (String, String) -> Unit) {
    var typed by remember(name) { mutableStateOf(name) }
    var pin by remember(name) { mutableStateOf("") }
    val valid = typed.isNotBlank() && typed != name && (!pinRequired || pin.length in 4..8)
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Name") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(typed, { if (it.length <= 64) typed = it }, label = { Text("Name") }, singleLine = true,
                enabled = !busy, modifier = Modifier.fillMaxWidth())
            if (pinRequired) ProfilePinField("Current PIN", pin, { pin = it }, !busy)
            failure?.let { Text(it, color = Palette.danger) }
        } },
        confirmButton = { TextButton({ if (!busy && valid) save(typed, pin) }, enabled = !busy && valid) { Text("Save") } },
        dismissButton = { TextButton(dismiss, enabled = !busy) { Text("Cancel") } })
}

@Composable
internal fun ProfileLockDialog(locked: Boolean, pinRequired: Boolean, busy: Boolean, failure: String?, dismiss: () -> Unit,
    save: (String, String) -> Unit) {
    var old by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    val needsOld = pinRequired && locked
    val valid = (!needsOld || old.length in 4..8) && (next.length in 4..8 || (locked && next.isEmpty()))
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text(if (locked) "Change profile PIN" else "Set profile PIN") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ask for a PIN before this profile opens.", color = Palette.secondary)
            if (needsOld) ProfilePinField("Current PIN", old, { old = it }, !busy)
            ProfilePinField(if (locked) "New PIN (blank removes the lock)" else "New PIN", next, { next = it }, !busy)
            failure?.let { Text(it, color = Palette.danger) }
        } },
        confirmButton = { TextButton({ if (!busy && valid) save(old, next) }, enabled = !busy && valid) {
            Text(if (locked && next.isEmpty()) "Remove PIN" else "Save PIN")
        } },
        dismissButton = { TextButton(dismiss, enabled = !busy) { Text("Cancel") } })
}

@Composable
private fun ProfilePinField(label: String, value: String, change: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(value, { change(it.filter(Char::isDigit).take(8)) }, Modifier.fillMaxWidth(), enabled = enabled,
        label = { Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
}

@Composable
internal fun DeleteProfileDialog(name: String, pinRequired: Boolean, busy: Boolean, failure: String?, dismiss: () -> Unit,
    delete: (String) -> Unit) {
    var pin by remember(name) { mutableStateOf("") }
    val valid = !pinRequired || pin.length in 4..8
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Delete $name?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This permanently removes this profile’s watch history, favorites and preferences.")
            if (pinRequired) ProfilePinField("Current PIN", pin, { pin = it }, !busy)
            failure?.let { Text(it, color = Palette.danger) }
        } },
        confirmButton = { TextButton({ if (!busy && valid) delete(pin) }, enabled = !busy && valid) {
            Text("Delete profile", color = Palette.danger)
        } },
        dismissButton = { TextButton(dismiss, enabled = !busy) { Text("Cancel") } })
}

@Composable
internal fun MaturityLimitDialog(current: Int?, busy: Boolean, failure: String?, dismiss: () -> Unit, choose: (Int) -> Unit) {
    var selected by remember(current) { mutableStateOf(current) }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Maturity limit") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Titles above this rating are hidden for this profile", color = Palette.secondary)
            maturityLimits.forEach { limit ->
                val checked = selected == limit.age
                Row(Modifier.fillMaxWidth().pointerSelectable(checked, enabled = !busy, role = Role.RadioButton,
                    shape = RoundedCornerShape(10.dp)) { selected = limit.age }
                    .heightIn(min = 44.dp).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(limit.label, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (checked) Icon(Lucide.Check, null, Modifier.size(18.dp))
                }
            }
            failure?.let { Text(it, color = Palette.danger) }
        } },
        confirmButton = { TextButton({ selected?.let(choose) }, enabled = !busy && selected != null) { Text("Save") } },
        dismissButton = { TextButton(dismiss, enabled = !busy) { Text("Cancel") } })
}

@Composable
internal fun HouseholdPinDialog(title: String, busy: Boolean, failure: String?, dismiss: () -> Unit, submit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Enter the household PIN to change this profile’s limits.", color = Palette.secondary)
            ProfilePinField("Household PIN", pin, { pin = it }, !busy)
            failure?.let { Text(it, color = Palette.danger) }
        } },
        confirmButton = { TextButton({ if (!busy && pin.length in 4..8) submit(pin) }, enabled = !busy && pin.length in 4..8) {
            Text("Continue")
        } },
        dismissButton = { TextButton(dismiss, enabled = !busy) { Text("Cancel") } })
}