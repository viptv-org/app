package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.connection.KidsPolicy
import org.viptv.app.connection.RatingLabel
import org.viptv.app.features.entry.AvatarPicker
import org.viptv.app.features.entry.AvatarPickerLayout
import org.viptv.app.features.entry.TvProfileLockDialog
import org.viptv.app.state.PROFILE_DELETE
import org.viptv.app.state.PROFILE_RENAME
import org.viptv.app.state.profileMutationTarget
import org.viptv.app.theme.Palette

@Composable
internal fun TvProfileNameDialog(name: String, pinRequired: Boolean, busy: Boolean, failure: String?, dismiss: () -> Unit,
    save: (String, String) -> Unit, pending: Boolean = false, scale: Float) {
    val blocked = busy || pending
    var typed by remember(name) { mutableStateOf(name) }
    var pin by remember(name) { mutableStateOf("") }
    val valid = typed.isNotBlank() && typed != name && (!pinRequired || pin.length in 4..8)
    val stops = rememberTvDialogStops(if (pinRequired) 4 else 3)
    val nameField = stops.first()
    val saveStop = stops[stops.size - 2]
    val cancelStop = stops.last()
    val close = { if (!blocked) dismiss() }
    TvDialogPanel(scale, "Name", stops, nameField, close, actions = {
        TvDialogAction(scale, if (pending) "Saving…" else "Save", saveStop, enabled = !blocked && valid) { save(typed, pin) }
        TvDialogAction(scale, "Cancel", cancelStop, enabled = !blocked) { dismiss() }
    }) {
        TvDialogField("Name", typed, scale, !blocked, nameField, onEscape = close) { if (it.length <= 64) typed = it }
        if (pinRequired) {
            Spacer(Modifier.height((24 * scale).dp))
            TvDialogField("Current PIN", pin, scale, !blocked, stops[1], secret = true, onEscape = close) { pin = pinDigits(it) }
        }
        TvFailure(failure, scale)
    }
}

@Composable
internal fun TvMaturityLimitDialog(policy: KidsPolicy, busy: Boolean, failure: String?, dismiss: () -> Unit,
    save: (String?, String?, Boolean) -> Unit, scale: Float) {
    var movie by remember(policy) { mutableStateOf(policy.movie_rating) }
    var series by remember(policy) { mutableStateOf(policy.series_rating) }
    var unrated by remember(policy) { mutableStateOf(policy.allow_unrated) }
    val movieStops = rememberTvDialogStops(policy.ratings.movie.size + 1)
    val seriesStops = rememberTvDialogStops(policy.ratings.series.size + 1)
    val toggleStop = rememberTvDialogStop()
    val saveStop = rememberTvDialogStop()
    val cancelStop = rememberTvDialogStop()
    val stops = buildList {
        if (policy.ratings.movie.isNotEmpty()) addAll(movieStops)
        if (policy.ratings.series.isNotEmpty()) addAll(seriesStops)
        add(toggleStop); add(saveStop); add(cancelStop)
    }
    // The remote opens on the option in force, as a chip's list does.
    val first = when {
        policy.ratings.movie.isNotEmpty() -> movieStops[policy.ratings.movie.indexOfFirst { it.label == movie } + 1]
        policy.ratings.series.isNotEmpty() -> seriesStops[policy.ratings.series.indexOfFirst { it.label == series } + 1]
        else -> toggleStop
    }
    val close = { if (!busy) dismiss() }
    TvDialogPanel(scale, "Maturity limit", stops, first, close, actions = {
        TvDialogAction(scale, "Save", saveStop, enabled = !busy) { save(movie, series, unrated) }
        TvDialogAction(scale, "Cancel", cancelStop, enabled = !busy) { dismiss() }
    }) {
        Text("Titles above this rating are hidden for this profile", color = Palette.secondary,
            fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp)
        if (policy.ratings.movie.isNotEmpty()) {
            Spacer(Modifier.height((24 * scale).dp))
            TvRatingRow("Movies up to", policy.ratings.movie, movie, !busy, scale, movieStops) { movie = it }
        }
        if (policy.ratings.series.isNotEmpty()) {
            Spacer(Modifier.height((24 * scale).dp))
            TvRatingRow("Series up to", policy.ratings.series, series, !busy, scale, seriesStops) { series = it }
        }
        Spacer(Modifier.height((28 * scale).dp))
        TvDialogToggle("Allow titles with no rating", "Unrated titles and titles whose ratings disagree stay hidden unless you allow them.",
            unrated, scale, toggleStop, !busy) { unrated = !unrated }
        TvFailure(failure, scale)
    }
}

@Composable
internal fun TvDeleteProfileDialog(name: String, pinRequired: Boolean, busy: Boolean, failure: String?, dismiss: () -> Unit,
    delete: (String) -> Unit, pending: Boolean = false, scale: Float) {
    val blocked = busy || pending
    var pin by remember(name) { mutableStateOf("") }
    val valid = !pinRequired || pin.length in 4..8
    val stops = rememberTvDialogStops(if (pinRequired) 3 else 2)
    val pinStop = stops.first().takeIf { pinRequired }
    val cancelStop = stops[stops.size - 2]
    val deleteStop = stops.last()
    val close = { if (!blocked) dismiss() }
    TvDialogPanel(scale, "Delete $name?", stops, cancelStop, close, actions = {
        TvDialogAction(scale, "Cancel", cancelStop, enabled = !blocked) { dismiss() }
        TvDialogAction(scale, if (pending) "Deleting…" else "Delete profile", deleteStop, danger = true, enabled = !blocked && valid) {
            delete(pin)
        }
    }) {
        Text("This permanently removes this profile’s watch history, favorites and preferences.",
            color = Palette.secondary, fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp)
        pinStop?.let {
            Spacer(Modifier.height((24 * scale).dp))
            TvDialogField("Current PIN", pin, scale, !blocked, it, secret = true, onEscape = close) { pin = pinDigits(it) }
        }
        TvFailure(failure, scale)
    }
}

@Composable
internal fun TvHouseholdPinDialog(title: String, busy: Boolean, failure: String?, dismiss: () -> Unit, submit: (String) -> Unit,
    scale: Float, note: String = "Enter the household PIN to change this profile.") {
    var pin by remember { mutableStateOf("") }
    val stops = rememberTvDialogStops(3)
    val pinStop = stops.first()
    val continueStop = stops[1]
    val cancelStop = stops.last()
    val close = { if (!busy) dismiss() }
    TvDialogPanel(scale, title, stops, pinStop, close, actions = {
        TvDialogAction(scale, "Continue", continueStop, enabled = !busy && pin.length in 4..8) { submit(pin) }
        TvDialogAction(scale, "Cancel", cancelStop, enabled = !busy) { dismiss() }
    }) {
        Text(note, color = Palette.secondary, fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp)
        Spacer(Modifier.height((24 * scale).dp))
        TvDialogField("Household PIN", pin, scale, !busy, pinStop, secret = true, onEscape = close) { pin = pinDigits(it) }
        TvFailure(failure, scale)
    }
}

@Composable
internal fun TvProfileDialogsContent(dialogs: ProfileDialogs, values: ProfilePageValues, busy: Boolean, error: String?,
    actions: ProfilePageActions, scale: Float) {
    val mutations = LocalPendingMutation.current
    if (dialogs.name) TvProfileNameDialog(values.profile.name, values.pinRequired, busy, error,
        dismiss = { if (!busy) dialogs.name = false },
        save = { name, pin ->
            if (values.householdGated) {
                dialogs.name = false
                dialogs.requireHouseholdPin("Change this profile’s name") { actions.rename(name, pin, it) }
            } else actions.rename(name, pin, "")
        },
        pending = mutations.isPending(profileMutationTarget(PROFILE_RENAME)), scale = scale)
    if (dialogs.avatar) AvatarPicker(values.profile, busy, AvatarPickerLayout.Television, { if (!busy) dialogs.avatar = false },
        choose = { pick, pin ->
            if (values.householdGated) {
                dialogs.avatar = false
                dialogs.requireHouseholdPin("Change this profile’s avatar") { actions.avatar(pick, pin, it) }
            } else actions.avatar(pick, pin, "")
        })
    if (dialogs.lock) TvProfileLockDialog(values.profile, busy, scale, { if (!busy) dialogs.lock = false },
        setPin = { old, next -> actions.lock(old, next) }, pinAvailable = true)
    if (dialogs.maturity) values.policy?.let { policy ->
        TvMaturityLimitDialog(policy, busy, error, dismiss = { if (!busy) dialogs.maturity = false },
            save = { movie, series, unrated -> dialogs.chooseMaturity(MaturitySelection(movie, series, unrated), values, actions) },
            scale = scale)
    }
    if (dialogs.delete) TvDeleteProfileDialog(values.profile.name, values.pinRequired, busy, error,
        dismiss = { if (!busy) dialogs.delete = false },
        delete = { pin ->
            if (values.householdGated) {
                dialogs.delete = false
                dialogs.requireHouseholdPin("Delete this profile") { actions.delete(pin, it) }
            } else actions.delete(pin, "")
        },
        pending = mutations.isPending(profileMutationTarget(PROFILE_DELETE)), scale = scale)
    dialogs.householdPinTitle?.let { title -> TvHouseholdPinDialog(title, busy, error,
        dismiss = { if (!busy) dialogs.clearHouseholdPin() }, submit = { pin -> dialogs.householdPinFor?.invoke(pin) }, scale = scale) }
}