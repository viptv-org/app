package org.viptv.app.features.settings

import org.viptv.app.components.AppTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.viptv.app.components.LocalPendingMutation
import org.viptv.app.components.TvAction
import org.viptv.app.components.pointerToggleable
import org.viptv.app.connection.KidsPolicy
import org.viptv.app.connection.RatingLabel
import org.viptv.app.features.entry.AvatarPicker
import org.viptv.app.features.entry.AvatarPickerLayout
import org.viptv.app.features.entry.TvProfileLockDialog
import org.viptv.app.state.PROFILE_DELETE
import org.viptv.app.state.PROFILE_RENAME
import org.viptv.app.state.profileMutationTarget
import org.viptv.app.theme.Palette

private val TvDialogDangerInk = Color(0xFFB42318)

// One focus stop of a TV panel: Up and Down walk the stops in the order the panel declares, so the remote never leaves the dialog.
internal class TvDialogStop {
    val requester = FocusRequester()
    var focused by mutableStateOf(false)
}

@Composable
internal fun rememberTvDialogStop() = remember { TvDialogStop() }

@Composable
internal fun rememberTvDialogStops(count: Int) = remember(count) { List(count) { TvDialogStop() } }

private fun Modifier.tvDialogStop(stop: TvDialogStop?) =
    if (stop == null) this else focusRequester(stop.requester).tvDialogFocusWatch(stop)

private fun Modifier.tvDialogFocusWatch(stop: TvDialogStop?) =
    if (stop == null) this else onFocusChanged { stop.focused = it.isFocused }

// focus names the stop the remote lands on when the panel opens.
@Composable
internal fun TvDialogPanel(scale: Float, title: String, stops: List<TvDialogStop>, focus: TvDialogStop?, dismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit, body: @Composable ColumnScope.() -> Unit) {
    Popup(onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).imePadding().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val current = stops.indexOfFirst { it.focused }
            when (event.key) {
                Key.Back, Key.Escape -> { dismiss(); true }
                Key.DirectionUp -> { stops.getOrNull(if (current < 0) 0 else current - 1)?.focus(); true }
                Key.DirectionDown -> { stops.getOrNull(if (current < 0) 0 else current + 1)?.focus(); true }
                else -> false
            }
        }, contentAlignment = Alignment.Center) {
            Column(Modifier.width((860 * scale).dp).heightIn(max = (900 * scale).dp).clip(RoundedCornerShape((24 * scale).dp))
                .background(Palette.raised).verticalScroll(rememberScrollState()).padding((48 * scale).dp)) {
                Text(title, fontSize = (44 * scale).sp, lineHeight = (50 * scale).sp, letterSpacing = (-.88f * scale).sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height((24 * scale).dp))
                body()
                Spacer(Modifier.height((36 * scale).dp))
                Row(horizontalArrangement = Arrangement.spacedBy((16 * scale).dp), content = actions)
            }
        }
        LaunchedEffect(Unit) {
            if (focus == null) return@LaunchedEffect
            repeat(60) {
                if (runCatching { focus.requester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                withFrameNanos { }
            }
        }
    }
}

private fun TvDialogStop.focus() {
    runCatching { requester.requestFocus() }
}

@Composable
private fun TvDialogAction(scale: Float, label: String, stop: TvDialogStop? = null, enabled: Boolean = true,
    danger: Boolean = false, activate: () -> Unit) {
    TvAction(Modifier.tvDialogFocusWatch(stop).widthIn(min = (240 * scale).dp).heightIn(min = (72 * scale).dp), scale,
        requester = stop?.requester, enabled = enabled, keepFocus = true, contentPadding = PaddingValues(horizontal = (34 * scale).dp),
        activate = activate) { ink ->
        Text(label, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.Bold,
            color = when {
                !danger -> ink
                ink == Palette.background -> TvDialogDangerInk
                else -> Palette.danger
            })
    }
}

@Composable
private fun TvDialogField(label: String, value: String, scale: Float, enabled: Boolean, stop: TvDialogStop? = null,
    secret: Boolean = false, onEscape: (() -> Unit)? = null, change: (String) -> Unit) {
    AppTextField(value, change, Modifier.tvDialogStop(stop).fillMaxWidth().heightIn(min = (80 * scale).dp), enabled = enabled, label = label,
        textStyle = LocalTextStyle.current.copy(fontSize = (26 * scale).sp, lineHeight = (34 * scale).sp),
        shape = RoundedCornerShape((20 * scale).dp),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.NumberPassword) else KeyboardOptions.Default,
        autocomplete = if (secret) "one-time-code" else "off", onEscape = onEscape)
}

private fun pinDigits(text: String) = text.filter(Char::isDigit).take(8)

@Composable
private fun TvDialogChip(label: String, selected: Boolean, scale: Float, stop: TvDialogStop?, enabled: Boolean = true,
    activate: () -> Unit) {
    TvAction(Modifier.tvDialogFocusWatch(stop).heightIn(min = (56 * scale).dp), scale, requester = stop?.requester, enabled = enabled,
        ring = false, cornerRadius = (28 * scale).dp, background = if (selected) Palette.text.copy(alpha = .22f) else Color(0xFF191919),
        contentPadding = PaddingValues(horizontal = (22 * scale).dp), activate = activate) { ink ->
        Text(label, color = ink, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun TvDialogToggle(label: String, note: String?, checked: Boolean, scale: Float, stop: TvDialogStop?,
    enabled: Boolean = true, toggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((14 * scale).dp)
    Row(Modifier.tvDialogStop(stop).fillMaxWidth().heightIn(min = (72 * scale).dp)
        .onFocusChanged { focused = it.isFocused }
        .background(if (focused) Palette.text else Color.White.copy(alpha = .06f), shape)
        .pointerToggleable(checked, enabled = enabled, role = Role.Switch, shape = shape, onValueChange = { toggle() })
        .padding(horizontal = (24 * scale).dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((20 * scale).dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
            Text(label, color = if (focused) Palette.background else Palette.text, fontSize = (26 * scale).sp,
                lineHeight = (32 * scale).sp, fontWeight = FontWeight.SemiBold)
            note?.let { Text(it, color = if (focused) Color(0xFF4A4945) else Palette.secondary, fontSize = (20 * scale).sp,
                lineHeight = (26 * scale).sp) }
        }
        TvSettingsSwitch(checked, scale)
    }
}

@Composable
private fun TvFailure(failure: String?, scale: Float) {
    failure?.let {
        Spacer(Modifier.height((20 * scale).dp))
        Text(it, color = Palette.danger, fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp)
    }
}

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
private fun TvRatingRow(title: String, labels: List<RatingLabel>, selected: String?, enabled: Boolean, scale: Float,
    stops: List<TvDialogStop>, choose: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy((12 * scale).dp)) {
        Text(title, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy((10 * scale).dp), verticalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
            TvDialogChip("All ratings", selected == null, scale, stops.first(), enabled) { choose(null) }
            labels.forEachIndexed { index, label ->
                TvDialogChip(label.label, selected == label.label, scale, stops[index + 1], enabled) { choose(label.label) }
            }
        }
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
    scale: Float) {
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
        Text("Enter the household PIN to change this profile’s limits.", color = Palette.secondary,
            fontSize = (26 * scale).sp, lineHeight = (38 * scale).sp)
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
        dismiss = { if (!busy) dialogs.name = false }, save = { name, pin -> actions.rename(name, pin) },
        pending = mutations.isPending(profileMutationTarget(PROFILE_RENAME)), scale = scale)
    if (dialogs.avatar) AvatarPicker(values.profile, busy, AvatarPickerLayout.Television, { if (!busy) dialogs.avatar = false },
        choose = { pick, pin -> actions.avatar(pick, pin) })
    if (dialogs.lock) TvProfileLockDialog(values.profile, busy, scale, { if (!busy) dialogs.lock = false },
        setPin = { old, next -> actions.lock(old, next) }, pinAvailable = true)
    if (dialogs.maturity) values.policy?.let { policy ->
        TvMaturityLimitDialog(policy, busy, error, dismiss = { if (!busy) dialogs.maturity = false },
            save = { movie, series, unrated -> dialogs.chooseMaturity(MaturitySelection(movie, series, unrated), values, actions) },
            scale = scale)
    }
    if (dialogs.delete) TvDeleteProfileDialog(values.profile.name, values.pinRequired, busy, error,
        dismiss = { if (!busy) dialogs.delete = false }, delete = { pin -> actions.delete(pin) },
        pending = mutations.isPending(profileMutationTarget(PROFILE_DELETE)), scale = scale)
    if (dialogs.kidsPin) TvHouseholdPinDialog("Turn on Kids profile", busy, error,
        dismiss = { if (!busy) dialogs.kidsPin = false }, submit = { pin -> actions.kids(true, pin) }, scale = scale)
    dialogs.maturityPin?.let { selection -> TvHouseholdPinDialog("Change the maturity limit", busy, error,
        dismiss = { if (!busy) dialogs.maturityPin = null },
        submit = { pin -> actions.maturity(selection.movie, selection.series, selection.unrated, pin) }, scale = scale) }
}