package org.viptv.app.features.entry

import org.viptv.app.components.AppTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TextButton
import org.viptv.app.components.pointerClickable
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.AlertDialog

@Composable
internal fun TvPinScreen(profile: Profile?, pin: String, busy: Boolean, error: String?,
    digit: (String) -> Unit, erase: () -> Unit, submit: () -> Unit, back: () -> Unit, leaving: Boolean = false) {
    val field = remember { FocusRequester() }
    var forgot by remember { mutableStateOf(false) }
    val recovery = remember { FocusRequester() }
    var restore by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(forgot, restore, windowFocused) {
        if (!forgot && restore && windowFocused) { withFrameNanos { }; recovery.requestFocus(); restore = false }
    }
    // Requesting focus in the first frame can run before the field is placed, leaving no focused control; retry until it is laid out.
    LaunchedEffect(busy) {
        if (busy) return@LaunchedEffect
        repeat(60) {
            if (runCatching { field.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    // The field takes the device IME and a physical or remote keyboard; typing it out is the same digits the grid used to hand over one at a time.
    val change: (String) -> Unit = { next ->
        val digits = next.filter(Char::isDigit).take(8)
        when {
            digits == pin -> Unit
            digits.length > pin.length && digits.startsWith(pin) -> digits.drop(pin.length).forEach { digit(it.toString()) }
            digits.length < pin.length && pin.startsWith(digits) -> repeat(pin.length - digits.length) { erase() }
            else -> { repeat(pin.length) { erase() }; digits.forEach { digit(it.toString()) } }
        }
    }
    val canSubmit = !busy && pin.length in 4..8
    BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false else when {
            event.key == Key.Escape -> { back(); true }
            else -> false
        }
    }) {
        val scale = maxWidth / 1920.dp
        Column(Modifier.align(Alignment.TopCenter).padding(top = 150.dp * scale), horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(profile, 120.dp * scale, 22.dp * scale)
            Spacer(Modifier.height(30.dp * scale))
            Text(if (leaving) "Enter the PIN to leave ${profile?.name.orEmpty()}’s profile" else if (profile?.lockKind == "parent") "Enter the account parent PIN" else "Enter the PIN for ${profile?.name.orEmpty()}",
                fontSize = (44 * scale).sp, lineHeight = (50.6f * scale).sp, letterSpacing = (-.88f * scale).sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(29.dp * scale))
            AppTextField(pin, change, Modifier.width(480.dp * scale).focusRequester(field), enabled = !busy, label = "PIN",
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = (26 * scale).sp, lineHeight = (34 * scale).sp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape((20 * scale).dp),
                visualTransformation = PasswordVisualTransformation(), autocomplete = "one-time-code",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSubmit) submit() }))
            Spacer(Modifier.height(24.dp * scale))
            var forgotFocused by remember { mutableStateOf(false) }
            Text("Forgot PIN?", Modifier
                .focusRequester(recovery)
                .onFocusChanged { forgotFocused = it.isFocused }
                .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !busy) { forgot = true },
                fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold,
                color = if (forgotFocused) Palette.text else Palette.secondary)
            error?.let { Text(it, Modifier.padding(top = 30.dp * scale), color = Palette.danger,
                fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp) }
        }
    }
    if (forgot) AlertDialog(onDismissRequest = { restore = true; forgot = false }, title = { Text("Profile PIN") },
        text = { Text("PIN recovery is not available for this profile.") },
        confirmButton = { TextButton(onClick = { restore = true; forgot = false }) { Text("OK") } })
}
