package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.theme.Palette

// The one TV text field: the device IME and a connected keyboard type into an editable node, never into a painted copy of the value.
@Composable
internal fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    scale: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
    secret: Boolean = false,
    numeric: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    submit: (() -> Unit)? = null,
    keepFocus: Boolean = false,
    requester: FocusRequester? = null,
    focusOnEnter: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onEscape: (() -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    val own = remember { FocusRequester() }
    val target = requester ?: own
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val radius = (20 * scale).dp
    val shape = RoundedCornerShape(radius)
    if (focusOnEnter) LaunchedEffect(Unit) {
        repeat(FIELD_FOCUS_RETRY_FRAMES) {
            if (runCatching { target.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
        label?.let {
            Text(it, fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold, color = Palette.body)
        }
        AppBasicTextField(value, { if (enabled) onValueChange(it) },
            Modifier.fillMaxWidth().heightIn(min = (80 * scale).dp).focusRequester(target)
                // The D-pad leaves the field: the device IME owns editing keys while it is open, and a closed one must not trap the remote.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        // Select reopens the IME that a Left or Down from elsewhere closed.
                        Key.DirectionCenter -> { keyboard?.show(); true }
                        Key.DirectionUp -> { focusManager.moveFocus(FocusDirection.Up); true }
                        Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                        Key.DirectionLeft -> { focusManager.moveFocus(FocusDirection.Left); true }
                        Key.DirectionRight -> { focusManager.moveFocus(FocusDirection.Right); true }
                        else -> false
                    }
                }
                .let { base -> if (onFocusChanged == null) base else base.onFocusChanged { onFocusChanged(it.isFocused) } }
                .semantics {
                    (label ?: placeholder).takeIf { it.isNotEmpty() }?.let { contentDescription = it }
                    if (!enabled) disabled()
                },
            label = label ?: placeholder,
            enabled = enabled || keepFocus,
            textStyle = LocalTextStyle.current.copy(fontSize = (28 * scale).sp, lineHeight = (34 * scale).sp,
                color = if (value.isEmpty()) Palette.secondary else Palette.text),
            cursorBrush = SolidColor(Palette.accent),
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    secret && numeric -> KeyboardType.NumberPassword
                    secret -> KeyboardType.Password
                    numeric -> KeyboardType.Number
                    else -> KeyboardType.Text
                },
                imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onDone = { submit?.invoke() }, onGo = { submit?.invoke() }, onNext = { submit?.invoke() },
                onSearch = { submit?.invoke() }, onSend = { submit?.invoke() }),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            autocomplete = if (secret) "current-password" else "off",
            onEscape = onEscape,
            interactionSource = interaction,
            decorationBox = { input ->
                Row(Modifier.fillMaxWidth().heightIn(min = (80 * scale).dp)
                    .graphicsLayer { alpha = if (enabled) 1f else .38f }
                    .tvFocusOutline(focused, radius, scale)
                    .clip(shape)
                    .background(Color.White.copy(alpha = .07f), shape)
                    .border((if (focused) 2 else 1).dp * scale, if (focused) Palette.text else Color.White.copy(alpha = .14f), shape)
                    .padding(horizontal = (28 * scale).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((16 * scale).dp)) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder.isNotEmpty())
                            Text(placeholder, fontSize = (28 * scale).sp, lineHeight = (34 * scale).sp, color = Palette.secondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        input()
                    }
                    trailing?.invoke()
                }
            })
    }
}

private const val FIELD_FOCUS_RETRY_FRAMES = 60