package org.viptv.app.features.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import org.viptv.app.components.FilterChip
import org.viptv.app.components.TvFocusScroll

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
internal fun LeavePinDialog(profileName: String, configured: Boolean, busy: Boolean, failure: String?,
    dismiss: () -> Unit, submit: (String, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    val mismatch = confirmation.isNotEmpty() && next != confirmation
    val canSave = (!configured || validParentPin(current)) && validParentPin(next) && next == confirmation
    val canRemove = !configured || validParentPin(current)
    LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() },
        title = { Text(if (configured) "Change ${profileName}’s leave PIN" else "Set ${profileName}’s leave PIN") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Ask for this PIN before leaving ${profileName}’s profile.", color = Palette.secondary)
            if (configured) ParentPinField("Current PIN", current, { current = it }, !busy, Modifier.focusRequester(first))
            ParentPinField("New PIN", next, { next = it }, !busy, if (configured) Modifier else Modifier.focusRequester(first))
            ParentPinField("Confirm new PIN", confirmation, { confirmation = it }, !busy,
                if (mismatch) Modifier.semantics { error("PINs do not match") } else Modifier, mismatch)
            Text(if (mismatch) "PINs do not match." else "Use 4 to 8 digits.", color = if (mismatch) Palette.danger else Palette.secondary)
            failure?.let { Text(it, color = Palette.danger) }
        } },
        confirmButton = { TextButton({ if (!busy && canSave) submit(current, next) }, enabled = !busy && canSave) { Text("Save PIN") } },
        dismissButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (configured) TextButton({ if (!busy && canRemove) submit(current, "") }, enabled = !busy && canRemove) {
                Text("Remove PIN", color = if (!busy && canRemove) Palette.danger else Palette.tertiary)
            }
            TextButton(dismiss, enabled = !busy) { Text("Cancel") }
        } })
}

@Composable
internal fun KidsPolicyDialog(profile: ServerProfile, policy: KidsPolicy, pinConfigured: Boolean, busy: Boolean,
    failure: String?, dismiss: () -> Unit, save: (KidsPolicy) -> Unit) {
    var enabled by remember(profile.id, policy) { mutableStateOf(policy.enabled) }
    var age by remember(profile.id, policy) { mutableStateOf(policy.max_age.toString()) }
    var movie by remember(profile.id, policy) { mutableStateOf(policy.movie_rating) }
    var series by remember(profile.id, policy) { mutableStateOf(policy.series_rating) }
    var unrated by remember(profile.id, policy) { mutableStateOf(policy.allow_unrated) }
    val parsed = age.toIntOrNull()
    val valid = parsed != null && parsed in 0..17 && (!enabled || pinConfigured)
    val editable = !busy && enabled
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Restrictions for ${profile.name}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Kids restrictions")
                Switch(enabled, { enabled = it }, enabled = !busy && pinConfigured)
            }
            if (!pinConfigured) Text("Set a parent PIN before enabling kids restrictions.", color = Palette.secondary)
            OutlinedTextField(age, { age = it.filter { c -> c in '0'..'9' }.take(2) },
                label = { Text("Maximum age") }, modifier = Modifier.focusRequester(first),
                supportingText = { Text("Used for any kind without its own rating limit.") },
                enabled = editable, singleLine = true, isError = enabled && (parsed == null || parsed !in 0..17),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (policy.ratings.movie.isNotEmpty())
                RatingChoices("Movies up to", policy.ratings.movie, movie, editable) { movie = it }
            if (policy.ratings.series.isNotEmpty())
                RatingChoices("Series up to", policy.ratings.series, series, editable) { series = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow titles with no rating")
                    Text("Unrated titles and titles whose ratings disagree stay hidden unless you allow them.",
                        color = Palette.secondary, style = MaterialTheme.typography.bodySmall)
                }
                Switch(unrated, { unrated = it }, enabled = editable)
            }
            failure?.let { Text(it, color = Palette.danger) }
        } }, confirmButton = { TextButton({
            if (valid && !busy) save(KidsPolicy(enabled, requireNotNull(parsed), movie, series, unrated))
        }, enabled = valid && !busy) { Text("Save restrictions") } },
        dismissButton = { TextButton(dismiss, enabled = !busy) { Text("Cancel") } })
}

@Composable
private fun RatingChoices(title: String, labels: List<RatingLabel>, selected: String?, enabled: Boolean,
    choose: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title)
        TvFocusScroll(horizontal = true) { Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected == null, { choose(null) }, enabled = enabled, label = { Text("All ratings") })
            labels.forEach { label ->
                FilterChip(selected == label.label, { choose(label.label) }, enabled = enabled, label = { Text(label.label) })
            }
        } }
    }
}