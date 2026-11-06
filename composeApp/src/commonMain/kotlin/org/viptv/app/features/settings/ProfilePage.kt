package org.viptv.app.features.settings

import org.viptv.app.components.AppTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import org.viptv.app.components.pageBottomClearance
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
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.MutationSpinner
import org.viptv.app.components.Switch
import org.viptv.app.components.TextButton
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.pointerToggleable
import org.viptv.app.connection.KidsPolicy
import org.viptv.app.connection.Profile
import org.viptv.app.features.entry.AvatarPick
import org.viptv.app.features.entry.AvatarPicker
import org.viptv.app.features.entry.AvatarPickerLayout
import org.viptv.app.features.entry.ProfileAvatar
import org.viptv.app.features.entry.requiresLocalPin
import org.viptv.app.state.PROFILE_DELETE
import org.viptv.app.state.PROFILE_PIN
import org.viptv.app.state.PROFILE_RENAME
import org.viptv.app.state.profileMutationTarget
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
    val policy: KidsPolicy? = null,
) {
    val pinRequired get() = profile.requiresLocalPin(local)
    val householdPinRequired get() = householdPinConfigured && !householdUnlocked
    val limitsKnown get() = policy != null
    fun kidsPolicy(): KidsPolicy? = policy?.copy(enabled = kids, max_age = maxAge ?: policy.max_age)
}

internal data class ProfilePageActions(
    val rename: (String, String) -> Unit = { _, _ -> },
    val avatar: (AvatarPick, String) -> Unit = { _, _ -> },
    val kids: (Boolean, String) -> Unit = { _, _ -> },
    val maturity: (String?, String?, Boolean, String) -> Unit = { _, _, _, _ -> },
    val approvedTitles: () -> Unit = {},
    val lock: (String, String) -> Unit = { _, _ -> },
    val parentalControls: () -> Unit = {},
    val language: () -> Unit = {},
    val delete: (String) -> Unit = {},
)

internal data class MaturitySelection(val movie: String?, val series: String?, val unrated: Boolean)

internal const val LIMITS_UNKNOWN = "Unavailable"

internal fun limitsUnknownNote(values: ProfilePageValues): String =
    if (values.householdPinRequired) "Enter the parent PIN to manage this household."
    else "Open Parental controls to try again."

internal fun maturityLimitValue(values: ProfilePageValues): String {
    if (!values.kids) return "All ratings"
    val policy = values.policy ?: return LIMITS_UNKNOWN
    if (policy.movie_rating == null && policy.series_rating == null) return "All ratings"
    return listOfNotNull(
        policy.movie_rating?.let { "Movies up to $it" },
        policy.series_rating?.let { "Series up to $it" },
    ).joinToString(" · ")
}

internal fun approvedTitlesValue(values: ProfilePageValues): String =
    values.approvedTitles?.toString() ?: LIMITS_UNKNOWN

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
    var maturityPin by mutableStateOf<MaturitySelection?>(null)

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

    fun chooseMaturity(selection: MaturitySelection, values: ProfilePageValues, actions: ProfilePageActions) {
        maturity = false
        if (values.householdPinRequired) maturityPin = selection
        else actions.maturity(selection.movie, selection.series, selection.unrated, "")
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
    retry: (() -> Unit)?, actions: ProfilePageActions, layout: SettingsLayout) {
    val dialogs = rememberProfileDialogs(values?.profile?.id.orEmpty(), saved)
    if (values == null) {
        Text("Choose a profile to edit it.", Modifier.padding(32.dp), color = Palette.secondary)
        return
    }
    val horizontal = if (layout == SettingsLayout.Phone) 16.dp else 32.dp
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = horizontal, vertical = 8.dp).pageBottomClearance(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (layout != SettingsLayout.Phone) Text("Profile", Modifier.padding(top = 16.dp), fontSize = 26.sp,
            fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(14.dp), color = Palette.surface) {
            Column {
                ProfileRow("Name", value = values.profile.name, enabled = !busy) { dialogs.name = true }
                SettingsDividerLine()
                ProfileRow("Avatar", leading = { ProfileAvatar(values.profile, 32.dp, 10.dp) }, enabled = !busy) { dialogs.avatar = true }
                if (!values.local) {
                    SettingsDividerLine()
                    ProfileRow("Kids profile",
                        note = if (values.limitsKnown) "Limits titles, search and Live for this profile."
                        else limitsUnknownNote(values),
                        checked = values.kids, enabled = !busy) {
                        if (values.limitsKnown) dialogs.toggleKids(values, actions) else actions.parentalControls()
                    }
                    SettingsDividerLine()
                    ProfileRow("Maturity limit",
                        note = if (values.limitsKnown) "Titles above this rating are hidden for this profile"
                        else limitsUnknownNote(values),
                        value = maturityLimitValue(values), enabled = !busy) {
                        if (values.limitsKnown) dialogs.maturity = true else actions.parentalControls()
                    }
                    if (values.kids) {
                        SettingsDividerLine()
                        ProfileRow("Approved titles",
                            note = if (values.limitsKnown) "Only these titles show in the kids library"
                            else limitsUnknownNote(values),
                            value = approvedTitlesValue(values), enabled = !busy) {
                            if (values.limitsKnown) actions.approvedTitles() else actions.parentalControls()
                        }
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
        if (error != null && retry != null) TextButton(retry, enabled = !busy) { Text("Retry") }
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
    val mutations = LocalPendingMutation.current
    if (dialogs.name) ProfileNameDialog(values.profile.name, values.pinRequired, busy, error,
        dismiss = { if (!busy) dialogs.name = false }, save = { name, pin -> actions.rename(name, pin) },
        pending = mutations.isPending(profileMutationTarget(PROFILE_RENAME)))
    if (dialogs.avatar) AvatarPicker(values.profile, busy, avatarLayout, { if (!busy) dialogs.avatar = false },
        choose = { pick, pin -> actions.avatar(pick, pin) })
    if (dialogs.lock) ProfileLockDialog(values.profile.locked, values.pinRequired, busy, error,
        dismiss = { if (!busy) dialogs.lock = false }, save = { old, next -> actions.lock(old, next) },
        pending = mutations.isPending(profileMutationTarget(PROFILE_PIN)))
    if (dialogs.maturity) values.policy?.let { policy ->
        MaturityLimitDialog(policy, busy, error, { if (!busy) dialogs.maturity = false }) { movie, series, unrated ->
            dialogs.chooseMaturity(MaturitySelection(movie, series, unrated), values, actions) }
    }
    if (dialogs.delete) DeleteProfileDialog(values.profile.name, values.pinRequired, busy, error,
        dismiss = { if (!busy) dialogs.delete = false }, delete = { pin -> actions.delete(pin) },
        pending = mutations.isPending(profileMutationTarget(PROFILE_DELETE)))
    if (dialogs.kidsPin) HouseholdPinDialog("Turn on Kids profile", busy, error, { if (!busy) dialogs.kidsPin = false }) { pin ->
        actions.kids(true, pin)
    }
    dialogs.maturityPin?.let { selection -> HouseholdPinDialog("Change the maturity limit", busy, error,
        { if (!busy) dialogs.maturityPin = null }) { pin -> actions.maturity(selection.movie, selection.series, selection.unrated, pin) } }
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
    save: (String, String) -> Unit, pending: Boolean = false) {
    val blocked = busy || pending
    var typed by remember(name) { mutableStateOf(name) }
    var pin by remember(name) { mutableStateOf("") }
    val valid = typed.isNotBlank() && typed != name && (!pinRequired || pin.length in 4..8)
    AlertDialog(onDismissRequest = { if (!blocked) dismiss() }, title = { Text("Name") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(typed, { if (it.length <= 64) typed = it }, label = "Name",
                enabled = !blocked, modifier = Modifier.fillMaxWidth(), onEscape = { if (!blocked) dismiss() })
            if (pinRequired) ProfilePinField("Current PIN", pin, { pin = it }, !blocked, onEscape = { if (!blocked) dismiss() })
            failure?.let { Text(it, color = Palette.danger) }
        } },
        confirmButton = { TextButton({ if (!blocked && valid) save(typed, pin) }, enabled = !blocked && valid) {
            if (pending) { MutationSpinner(16.dp); Spacer(Modifier.width(8.dp)); Text("Saving…") } else Text("Save")
        } },
        dismissButton = { TextButton(dismiss, enabled = !blocked) { Text("Cancel") } })
}

@Composable
internal fun ProfileLockDialog(locked: Boolean, pinRequired: Boolean, busy: Boolean, failure: String?, dismiss: () -> Unit,
    save: (String, String) -> Unit, pending: Boolean = false) {
    val blocked = busy || pending
    var old by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    val needsOld = pinRequired && locked
    val valid = (!needsOld || old.length in 4..8) && (next.length in 4..8 || (locked && next.isEmpty()))
    AlertDialog(onDismissRequest = { if (!blocked) dismiss() }, title = { Text(if (locked) "Change profile PIN" else "Set profile PIN") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ask for a PIN before this profile opens.", color = Palette.secondary)
            if (needsOld) ProfilePinField("Current PIN", old, { old = it }, !blocked, onEscape = { if (!blocked) dismiss() })
            ProfilePinField(if (locked) "New PIN (blank removes the lock)" else "New PIN", next, { next = it }, !blocked, onEscape = { if (!blocked) dismiss() })
            failure?.let { Text(it, color = Palette.danger) }
        } },
        confirmButton = { TextButton({ if (!blocked && valid) save(old, next) }, enabled = !blocked && valid) {
            if (pending) { MutationSpinner(16.dp); Spacer(Modifier.width(8.dp)); Text("Saving…") }
            else Text(if (locked && next.isEmpty()) "Remove PIN" else "Save PIN")
        } },
        dismissButton = { TextButton(dismiss, enabled = !blocked) { Text("Cancel") } })
}

@Composable
private fun ProfilePinField(label: String, value: String, change: (String) -> Unit, enabled: Boolean, onEscape: (() -> Unit)? = null) {
    AppTextField(value, { change(it.filter(Char::isDigit).take(8)) }, Modifier.fillMaxWidth(), enabled = enabled,
        label = label, visualTransformation = PasswordVisualTransformation(), autocomplete = "one-time-code",
        onEscape = onEscape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
}

@Composable
internal fun DeleteProfileDialog(name: String, pinRequired: Boolean, busy: Boolean, failure: String?, dismiss: () -> Unit,
    delete: (String) -> Unit, pending: Boolean = false) {
    val blocked = busy || pending
    var pin by remember(name) { mutableStateOf("") }
    val valid = !pinRequired || pin.length in 4..8
    AlertDialog(onDismissRequest = { if (!blocked) dismiss() }, title = { Text("Delete $name?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This permanently removes this profile’s watch history, favorites and preferences.")
            if (pinRequired) ProfilePinField("Current PIN", pin, { pin = it }, !blocked, onEscape = { if (!blocked) dismiss() })
            failure?.let { Text(it, color = Palette.danger) }
        } },
        confirmButton = { TextButton({ if (!blocked && valid) delete(pin) }, enabled = !blocked && valid) {
            if (pending) { MutationSpinner(16.dp); Spacer(Modifier.width(8.dp)); Text("Deleting…") }
            else Text("Delete profile", color = Palette.danger)
        } },
        dismissButton = { TextButton(dismiss, enabled = !blocked) { Text("Cancel") } })
}

@Composable
internal fun MaturityLimitDialog(policy: KidsPolicy, busy: Boolean, failure: String?, dismiss: () -> Unit,
    save: (String?, String?, Boolean) -> Unit) {
    var movie by remember(policy) { mutableStateOf(policy.movie_rating) }
    var series by remember(policy) { mutableStateOf(policy.series_rating) }
    var unrated by remember(policy) { mutableStateOf(policy.allow_unrated) }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Maturity limit") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Titles above this rating are hidden for this profile", color = Palette.secondary)
            if (policy.ratings.movie.isNotEmpty())
                RatingChoices("Movies up to", policy.ratings.movie, movie, !busy) { movie = it }
            if (policy.ratings.series.isNotEmpty())
                RatingChoices("Series up to", policy.ratings.series, series, !busy) { series = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow titles with no rating")
                    Text("Unrated titles and titles whose ratings disagree stay hidden unless you allow them.",
                        color = Palette.secondary, style = MaterialTheme.typography.bodySmall)
                }
                Switch(unrated, { unrated = it }, enabled = !busy)
            }
            failure?.let { Text(it, color = Palette.danger) }
        } },
        confirmButton = { TextButton({ if (!busy) save(movie, series, unrated) }, enabled = !busy) { Text("Save") } },
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