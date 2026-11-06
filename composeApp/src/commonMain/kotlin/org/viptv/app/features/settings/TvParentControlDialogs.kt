package org.viptv.app.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.connection.KidsPolicy
import org.viptv.app.connection.RatingLabel
import org.viptv.app.connection.ServerProfile
import org.viptv.app.connection.validParentPin
import org.viptv.app.theme.Palette

@Composable
internal fun TvParentPinDialog(mode: String, configured: Boolean, busy: Boolean, failure: String?, dismiss: () -> Unit,
    submit: (String, String) -> Unit, pending: Boolean = false, scale: Float) {
    val blocked = busy || pending
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val needsCurrent = configured || mode != "save"
    val changesPin = mode == "save"
    val mismatch = confirmation.isNotEmpty() && next != confirmation
    val valid = (!needsCurrent || validParentPin(current)) && (!changesPin || validParentPin(next) && next == confirmation)
    val removing = mode == "remove"
    val stops = rememberTvDialogStops((if (needsCurrent) 1 else 0) + (if (changesPin) 2 else 0) + 2)
    val first = stops.first()
    val currentStop = if (needsCurrent) stops.first() else null
    val nextStop = if (changesPin) stops[if (needsCurrent) 1 else 0] else null
    val confirmStop = if (changesPin) stops[if (needsCurrent) 2 else 1] else null
    // The stop order is the row order: a destructive panel puts Cancel before its action.
    val primaryStop = if (removing) stops.last() else stops[stops.size - 2]
    val cancelStop = if (removing) stops[stops.size - 2] else stops.last()
    val primary = when (mode) { "unlock" -> "Unlock"; "remove" -> "Remove PIN"; else -> if (pending) "Saving…" else "Save PIN" }
    val close = { if (!blocked) dismiss() }
    TvDialogPanel(scale, when (mode) {
        "unlock" -> "Unlock parental controls"
        "remove" -> "Remove parent PIN?"
        else -> if (configured) "Change parent PIN" else "Set parent PIN"
    }, stops, if (removing) cancelStop else first, close, actions = {
        if (removing) TvDialogAction(scale, "Cancel", cancelStop, enabled = !blocked) { dismiss() }
        TvDialogAction(scale, primary, primaryStop, danger = removing, enabled = !blocked && valid) {
            submit(current, if (changesPin) next else "")
        }
        if (!removing) TvDialogAction(scale, "Cancel", cancelStop, enabled = !blocked) { dismiss() }
    }) {
        if (removing) Text("The household PIN will be removed. Kids restrictions must be off for every profile.",
            color = Palette.secondary, fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp)
        currentStop?.let { stop ->
            if (!removing) Spacer(Modifier.height((24 * scale).dp))
            TvDialogField(if (mode == "unlock") "Parent PIN" else "Current PIN", current, scale, !blocked, stop, secret = true,
                onEscape = close) { current = pinDigits(it) }
        }
        if (changesPin) {
            Spacer(Modifier.height((16 * scale).dp))
            TvDialogField("New PIN", next, scale, !blocked, nextStop, secret = true, onEscape = close) { next = pinDigits(it) }
            Spacer(Modifier.height((16 * scale).dp))
            TvDialogField("Confirm new PIN", confirmation, scale, !blocked, confirmStop, secret = true, onEscape = close) {
                confirmation = pinDigits(it)
            }
            Spacer(Modifier.height((12 * scale).dp))
            Text(if (mismatch) "PINs do not match." else "Use 4 to 8 digits. Keep your parent PIN private.",
                color = if (mismatch) Palette.danger else Palette.secondary, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
        }
        TvFailure(failure, scale)
    }
}

@Composable
internal fun TvKidsPolicyDialog(profile: ServerProfile, policy: KidsPolicy, pinConfigured: Boolean, busy: Boolean,
    failure: String?, dismiss: () -> Unit, save: (KidsPolicy) -> Unit, pending: Boolean = false, scale: Float) {
    val blocked = busy || pending
    var enabled by remember(profile.id, policy) { mutableStateOf(policy.enabled) }
    var age by remember(profile.id, policy) { mutableStateOf(policy.max_age.toString()) }
    var movie by remember(profile.id, policy) { mutableStateOf(policy.movie_rating) }
    var series by remember(profile.id, policy) { mutableStateOf(policy.series_rating) }
    var unrated by remember(profile.id, policy) { mutableStateOf(policy.allow_unrated) }
    val parsed = age.toIntOrNull()
    val valid = parsed != null && parsed in 0..17 && (!enabled || pinConfigured)
    val editable = !busy && enabled
    val movieStops = rememberTvDialogStops(policy.ratings.movie.size + 1)
    val seriesStops = rememberTvDialogStops(policy.ratings.series.size + 1)
    val toggleStop = rememberTvDialogStop()
    val ageStop = rememberTvDialogStop()
    val unratedStop = rememberTvDialogStop()
    val saveStop = rememberTvDialogStop()
    val cancelStop = rememberTvDialogStop()
    val stops = buildList {
        add(toggleStop); add(ageStop)
        if (policy.ratings.movie.isNotEmpty()) addAll(movieStops)
        if (policy.ratings.series.isNotEmpty()) addAll(seriesStops)
        add(unratedStop); add(saveStop); add(cancelStop)
    }
    val close = { if (!blocked) dismiss() }
    TvDialogPanel(scale, "Restrictions for ${profile.name}", stops, if (pinConfigured) toggleStop else saveStop, close, actions = {
        TvDialogAction(scale, if (pending) "Saving…" else "Save restrictions", saveStop, enabled = valid && !blocked) {
            save(KidsPolicy(enabled, requireNotNull(parsed), movie, series, unrated))
        }
        TvDialogAction(scale, "Cancel", cancelStop, enabled = !blocked) { dismiss() }
    }) {
        TvDialogToggle("Kids restrictions", null, enabled, scale, toggleStop, !busy && pinConfigured) { enabled = !enabled }
        if (!pinConfigured) {
            Spacer(Modifier.height((10 * scale).dp))
            Text("Set a parent PIN before enabling kids restrictions.", color = Palette.secondary,
                fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
        }
        Spacer(Modifier.height((20 * scale).dp))
        TvDialogField("Maximum age", age, scale, editable, ageStop, invalid = enabled && (parsed == null || parsed !in 0..17),
            onEscape = close) { age = it.filter { c -> c in '0'..'9' }.take(2) }
        Spacer(Modifier.height((8 * scale).dp))
        Text("Used for any kind without its own rating limit.", color = Palette.secondary,
            fontSize = (20 * scale).sp, lineHeight = (28 * scale).sp)
        if (policy.ratings.movie.isNotEmpty()) {
            Spacer(Modifier.height((24 * scale).dp))
            TvRatingRow("Movies up to", policy.ratings.movie, movie, editable, scale, movieStops) { movie = it }
        }
        if (policy.ratings.series.isNotEmpty()) {
            Spacer(Modifier.height((24 * scale).dp))
            TvRatingRow("Series up to", policy.ratings.series, series, editable, scale, seriesStops) { series = it }
        }
        Spacer(Modifier.height((24 * scale).dp))
        TvDialogToggle("Allow titles with no rating",
            "Unrated titles and titles whose ratings disagree stay hidden unless you allow them.",
            unrated, scale, unratedStop, editable) { unrated = !unrated }
        TvFailure(failure, scale)
    }
}

@Composable
internal fun TvLeavePinDialog(profileName: String, configured: Boolean, busy: Boolean, failure: String?, dismiss: () -> Unit,
    submit: (String, String) -> Unit, pending: Boolean = false, scale: Float) {
    val blocked = busy || pending
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val mismatch = confirmation.isNotEmpty() && next != confirmation
    val canSave = (!configured || validParentPin(current)) && validParentPin(next) && next == confirmation
    val canRemove = !configured || validParentPin(current)
    val stops = rememberTvDialogStops(if (configured) 6 else 5)
    val currentStop = if (configured) stops.first() else null
    val nextStop = stops[if (configured) 1 else 0]
    val confirmStop = stops[if (configured) 2 else 1]
    val saveStop = stops[stops.size - (if (configured) 3 else 2)]
    val removeStop = if (configured) stops[stops.size - 2] else null
    val cancelStop = stops.last()
    val close = { if (!blocked) dismiss() }
    TvDialogPanel(scale, if (configured) "Change $profileName’s leave PIN" else "Set $profileName’s leave PIN", stops,
        currentStop ?: nextStop, close, actions = {
            TvDialogAction(scale, if (pending) "Saving…" else "Save PIN", saveStop, enabled = !blocked && canSave) {
                submit(current, next)
            }
            removeStop?.let { stop ->
                TvDialogAction(scale, "Remove PIN", stop, danger = true, enabled = !blocked && canRemove) { submit(current, "") }
            }
            TvDialogAction(scale, "Cancel", cancelStop, enabled = !blocked) { dismiss() }
        }) {
        Text("Ask for this PIN before leaving $profileName’s profile.", color = Palette.secondary,
            fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp)
        currentStop?.let { stop ->
            Spacer(Modifier.height((24 * scale).dp))
            TvDialogField("Current PIN", current, scale, !blocked, stop, secret = true, onEscape = close) { current = pinDigits(it) }
        }
        Spacer(Modifier.height((16 * scale).dp))
        TvDialogField("New PIN", next, scale, !blocked, nextStop, secret = true, onEscape = close) { next = pinDigits(it) }
        Spacer(Modifier.height((16 * scale).dp))
        TvDialogField("Confirm new PIN", confirmation, scale, !blocked, confirmStop, secret = true, onEscape = close) {
            confirmation = pinDigits(it)
        }
        Spacer(Modifier.height((12 * scale).dp))
        Text(if (mismatch) "PINs do not match." else "Use 4 to 8 digits.",
            color = if (mismatch) Palette.danger else Palette.secondary, fontSize = (22 * scale).sp, lineHeight = (30 * scale).sp)
        TvFailure(failure, scale)
    }
}
