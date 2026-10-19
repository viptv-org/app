package org.viptv.app.features.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette

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
    // Requesting focus in the first frame can run before the key is placed
    // (the requester exists but is not attached yet), which left the PIN screen
    // with no focused control at all. Retry until the target is laid out.
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
        Row(Modifier.align(Alignment.TopCenter).padding(top = 380.dp * scale), horizontalArrangement = Arrangement.spacedBy(18.dp * scale)) {
            repeat(if (pin.length > 4) 8 else 4) { index ->
                val shape = RoundedCornerShape(14.dp * scale)
                Box(Modifier.size(88.dp * scale, 104.dp * scale).background(androidx.compose.ui.graphics.Color.White.copy(alpha = .08f), shape)
                    .border(2.dp * scale, if (index == pin.length) Palette.text else androidx.compose.ui.graphics.Color.White.copy(alpha = .14f), shape)
                    .semantics { contentDescription = "PIN digit ${index + 1}, ${if (index < pin.length) "filled" else "empty"}" },
                    contentAlignment = Alignment.Center) {
                    if (index < pin.length) Box(Modifier.size(22.dp * scale).background(Palette.text, CircleShape))
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = 540.dp * scale), verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("OK", "0", "Erase")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    row.forEach { value ->
                        if (value == "OK" && pin.length < 4) Spacer(Modifier.size(136.dp * scale, 76.dp * scale))
                        else TvPinKey(value, scale, !busy, if (value == "5") first else null) {
                            when (value) { "OK" -> submit(); "Erase" -> erase(); else -> digit(value) }
                        }
                    }
                }
            }
        }
        var forgotFocused by remember { mutableStateOf(false) }
        Text("Forgot PIN?", Modifier.align(Alignment.TopCenter).padding(top = 920.dp * scale)
            .focusRequester(recovery)
            .onFocusChanged { forgotFocused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !busy) { forgot = true },
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
