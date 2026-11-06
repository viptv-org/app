package org.viptv.app.features.settings

import org.viptv.app.components.AppTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.viptv.app.components.TvAction
import org.viptv.app.connection.RatingLabel
import org.viptv.app.components.pointerToggleable
import org.viptv.app.theme.Palette

internal val TvDialogDangerInk = Color(0xFFB42318)

// One focus stop of a TV panel: Up and Down walk the stops in the order the panel declares, so the remote never leaves the dialog.
internal const val TvDialogPanelTag = "tv-dialog-panel"

internal class TvDialogStop {
    val requester = FocusRequester()
    var focused by mutableStateOf(false)
}

@Composable
internal fun rememberTvDialogStop() = remember { TvDialogStop() }

@Composable
internal fun rememberTvDialogStops(count: Int) = remember(count) { List(count) { TvDialogStop() } }

internal fun Modifier.tvDialogStop(stop: TvDialogStop?) =
    if (stop == null) this else focusRequester(stop.requester).tvDialogFocusWatch(stop)

internal fun Modifier.tvDialogFocusWatch(stop: TvDialogStop?) =
    if (stop == null) this else onFocusChanged { stop.focused = it.isFocused }

// focus names the stop the remote lands on when the panel opens.
@Composable
internal fun TvDialogPanel(scale: Float, title: String, stops: List<TvDialogStop>, focus: TvDialogStop?, dismiss: () -> Unit,
    actions: @Composable FlowRowScope.() -> Unit, body: @Composable ColumnScope.() -> Unit) {
    Popup(onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).imePadding().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val current = stops.indexOfFirst { it.focused }
            when (event.key) {
                Key.Back, Key.Escape -> { dismiss(); true }
                Key.DirectionUp -> { walkStops(stops, current, -1); true }
                Key.DirectionDown -> { walkStops(stops, current, 1); true }
                else -> false
            }
        }, contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // The body scrolls; the title and the actions never leave the panel, however short the window.
                val panelHeight = maxHeight * .92f
                Column(Modifier.testTag(TvDialogPanelTag).width((860 * scale).dp).heightIn(max = panelHeight).clip(RoundedCornerShape((24 * scale).dp))
                    .background(Palette.raised)) {
                    Text(title, Modifier.padding(start = (48 * scale).dp, top = (48 * scale).dp, end = (48 * scale).dp),
                        fontSize = (44 * scale).sp, lineHeight = (50 * scale).sp, letterSpacing = (-.88f * scale).sp,
                        fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .padding(start = (48 * scale).dp, top = (24 * scale).dp, end = (48 * scale).dp)) {
                        body()
                    }
                    FlowRow(Modifier.fillMaxWidth().padding(start = (48 * scale).dp, end = (48 * scale).dp, top = (36 * scale).dp,
                        bottom = (48 * scale).dp), horizontalArrangement = Arrangement.spacedBy((16 * scale).dp, Alignment.Start),
                        verticalArrangement = Arrangement.spacedBy((16 * scale).dp), content = actions)
                }
            }
        }
        LaunchedEffect(focus) {
            repeat(60) { attempt ->
                if (focus?.focus() == true) return@LaunchedEffect
                // A control the server would reject is not a focus stop, so land on the first one that is — once the named one has had a frame to be placed.
                if (attempt >= 10) stops.forEach { if (it.focus()) return@LaunchedEffect }
                withFrameNanos { }
            }
        }
    }
}

internal fun TvDialogStop.focus(): Boolean = runCatching { requester.requestFocus() }.getOrDefault(false)

private fun walkStops(stops: List<TvDialogStop>, from: Int, step: Int) {
    var index = if (from < 0) 0 else from + step
    while (index in stops.indices) {
        if (stops[index].focus()) return
        index += step
    }
}

@Composable
internal fun TvDialogAction(scale: Float, label: String, stop: TvDialogStop? = null, enabled: Boolean = true,
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
internal fun TvDialogField(label: String, value: String, scale: Float, enabled: Boolean, stop: TvDialogStop? = null,
    secret: Boolean = false, invalid: Boolean = false, onEscape: (() -> Unit)? = null, change: (String) -> Unit) {
    AppTextField(value, change, Modifier.tvDialogStop(stop).fillMaxWidth().heightIn(min = (80 * scale).dp), enabled = enabled,
        label = label, isError = invalid,
        textStyle = LocalTextStyle.current.copy(fontSize = (26 * scale).sp, lineHeight = (34 * scale).sp),
        shape = RoundedCornerShape((20 * scale).dp),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.NumberPassword) else KeyboardOptions.Default,
        autocomplete = if (secret) "one-time-code" else "off", onEscape = onEscape)
}

internal fun pinDigits(text: String) = text.filter(Char::isDigit).take(8)

/** The one TV confirmation: title, the line that says what happens, Cancel focused first, and the action itself in the danger style when it destroys. */
@Composable
internal fun TvConfirmDialog(scale: Float, title: String, body: String, confirm: String, dismissLabel: String = "Cancel",
    danger: Boolean = false, busy: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancel = rememberTvDialogStop()
    val accept = rememberTvDialogStop()
    TvDialogPanel(scale, title, stops = listOf(cancel, accept), focus = cancel, dismiss = { if (!busy) onDismiss() }, actions = {
        TvDialogAction(scale, dismissLabel, stop = cancel, enabled = !busy) { if (!busy) onDismiss() }
        TvDialogAction(scale, confirm, stop = accept, enabled = !busy, danger = danger) { if (!busy) onConfirm() }
    }) { Text(body, color = Palette.secondary, fontSize = (26 * scale).sp, lineHeight = (36 * scale).sp) }
}

/** One row of a TV choice list: the panel's full width, the current value checked, the remote's ring on the row it stands on. */
@Composable
internal fun TvDialogChoiceRow(label: String, selected: Boolean, scale: Float, stop: TvDialogStop, activate: () -> Unit) {
    TvAction(Modifier.tvDialogStop(stop).fillMaxWidth().heightIn(min = (72 * scale).dp).semantics { if (selected) this.selected = true },
        scale, requester = stop.requester, ring = false, contentPadding = PaddingValues(horizontal = (28 * scale).dp),
        arrangement = Arrangement.Start, activate = activate) { ink ->
        Text(label, color = ink, fontSize = (26 * scale).sp, lineHeight = (32 * scale).sp, fontWeight = FontWeight.SemiBold)
        if (selected) {
            Spacer(Modifier.weight(1f))
            Icon(Lucide.Check, null, Modifier.size((26 * scale).dp), ink)
        }
    }
}

/** A TV panel that only tells the viewer something: one action, focused, and Back closes it. */
@Composable
internal fun TvNoticeDialog(scale: Float, title: String, body: String, action: String = "OK", onDismiss: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {}) {
    val close = rememberTvDialogStop()
    TvDialogPanel(scale, title, stops = listOf(close), focus = close, dismiss = onDismiss, actions = {
        TvDialogAction(scale, action, stop = close, activate = onDismiss)
    }) {
        Text(body, color = Palette.secondary, fontSize = (26 * scale).sp, lineHeight = (36 * scale).sp)
        extra()
    }
}

@Composable
internal fun TvDialogChip(label: String, selected: Boolean, scale: Float, stop: TvDialogStop?, enabled: Boolean = true,
    activate: () -> Unit) {
    TvAction(Modifier.tvDialogFocusWatch(stop).heightIn(min = (56 * scale).dp), scale, requester = stop?.requester, enabled = enabled,
        ring = false, cornerRadius = (28 * scale).dp, background = if (selected) Palette.text.copy(alpha = .22f) else Color(0xFF191919),
        contentPadding = PaddingValues(horizontal = (22 * scale).dp), activate = activate) { ink ->
        Text(label, color = ink, fontSize = (22 * scale).sp, lineHeight = (28 * scale).sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
internal fun TvDialogToggle(label: String, note: String?, checked: Boolean, scale: Float, stop: TvDialogStop?,
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
internal fun TvFailure(failure: String?, scale: Float) {
    failure?.let {
        Spacer(Modifier.height((20 * scale).dp))
        Text(it, color = Palette.danger, fontSize = (24 * scale).sp, lineHeight = (32 * scale).sp)
    }
}

@Composable
internal fun TvRatingRow(title: String, labels: List<RatingLabel>, selected: String?, enabled: Boolean, scale: Float,
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
