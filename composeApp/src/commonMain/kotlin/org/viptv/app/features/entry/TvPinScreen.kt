package org.viptv.app.features.entry

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalWindowInfo
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable
import org.viptv.app.components.TextButton

@Composable
internal fun TvPinScreen(profile: Profile?, pin: String, busy: Boolean, error: String?,
    digit: (String) -> Unit, erase: () -> Unit, submit: () -> Unit, back: () -> Unit) {
    val first = remember { FocusRequester() }
    var forgot by remember { mutableStateOf(false) }
    val recovery = remember { FocusRequester() }
    var restore by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(forgot, restore, windowFocused) {
        if (!forgot && restore && windowFocused) { withFrameNanos { }; recovery.requestFocus(); restore = false }
    }
    // Requesting focus in the first frame can run before the key is placed, leaving no focused control; retry until it is laid out.
    LaunchedEffect(busy) {
        if (busy) return@LaunchedEffect
        repeat(60) {
            if (runCatching { first.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    val numberKeys = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || busy) false else when {
            event.key in numberKeys -> { digit(numberKeys.indexOf(event.key).toString()); true }
            event.key == Key.Escape -> { back(); true }
            event.key == Key.Backspace -> { erase(); true }
            event.key == Key.Enter -> { submit(); true }
            else -> false
        }
    }) {
        val scale = maxWidth / 1920.dp
        ProfileAvatarAtTop(profile, scale)
        Text(if (profile?.lockKind == "parent") "Enter the account parent PIN" else "Enter the PIN for ${profile?.name.orEmpty()}", Modifier.align(Alignment.TopCenter).padding(top = 300.dp * scale),
            fontSize = (44 * scale).sp, lineHeight = (50.6f * scale).sp, letterSpacing = (-.88f * scale).sp, fontWeight = FontWeight.Bold)
        TvPinDigits(pin, scale, Modifier.align(Alignment.TopCenter).padding(top = 380.dp * scale))
        TvPinKeys(pin, scale, busy, first, digit, erase, submit, Modifier.align(Alignment.TopCenter).padding(top = 540.dp * scale))
        var forgotFocused by remember { mutableStateOf(false) }
        Text("Forgot PIN?", Modifier.align(Alignment.TopCenter).padding(top = 920.dp * scale)
            .focusRequester(recovery)
            .onFocusChanged { forgotFocused = it.isFocused }
            .pointerClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !busy) { forgot = true },
            fontSize = (24 * scale).sp, lineHeight = (30 * scale).sp, fontWeight = FontWeight.SemiBold,
            color = if (forgotFocused) Palette.text else Palette.secondary)
        error?.let { Text(it, Modifier.align(Alignment.TopCenter).padding(top = 980.dp * scale), color = Palette.danger,
            fontSize = (20 * scale).sp, lineHeight = (26 * scale).sp) }
    }
    if (forgot) AlertDialog(onDismissRequest = { restore = true; forgot = false }, title = { Text("Profile PIN") },
        text = { Text("PIN recovery is not available for this profile.") },
        confirmButton = { TextButton(onClick = { restore = true; forgot = false }) { Text("OK") } })
}

@Composable
private fun BoxScope.ProfileAvatarAtTop(profile: Profile?, scale: Float) {
    Box(Modifier.align(Alignment.TopCenter).padding(top = 150.dp * scale)) { ProfileAvatar(profile, 120.dp * scale, 22.dp * scale) }
}
