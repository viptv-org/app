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
import com.composables.icons.lucide.*
import org.viptv.app.connection.Profile
import org.viptv.app.theme.Palette

@Composable
internal fun TvPinScreen(profile: Profile?, pin: String, busy: Boolean, error: String?,
    digit: (String) -> Unit, erase: () -> Unit, submit: () -> Unit, back: () -> Unit) {
    val first = remember { FocusRequester() }
    var forgot by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { first.requestFocus() }
    val numberKeys = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
    BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || busy) false else when {
            event.key in numberKeys -> { digit(numberKeys.indexOf(event.key).toString()); true }
            event.key == Key.Escape -> { back(); true }
            event.key == Key.Backspace -> { erase(); true }
            event.key == Key.Enter && pin.length in 4..8 -> { submit(); true }
            else -> false
        }
    }) {
        val scale = maxWidth / 1920.dp
        Column(Modifier.align(Alignment.TopCenter).padding(top = 150.dp * scale), horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(profile, 120.dp * scale, 24.dp * scale)
            Spacer(Modifier.height(26.dp * scale))
            Text("Enter the PIN for ${profile?.name.orEmpty()}", fontSize = (44 * scale).sp,
                lineHeight = (56 * scale).sp, letterSpacing = (-0.88f * scale).sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(28.dp * scale))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp * scale)) {
                repeat(maxOf(4, pin.length)) { index ->
                    Box(Modifier.size(88.dp * scale, 104.dp * scale).background(Palette.raised, RoundedCornerShape(16.dp * scale))
                        .border(2.dp * scale, if (index == pin.length) Palette.text else androidx.compose.ui.graphics.Color(0xFF3A3A3C), RoundedCornerShape(16.dp * scale)),
                        contentAlignment = Alignment.Center) {
                        if (index < pin.length) Box(Modifier.size(22.dp * scale).background(Palette.text, CircleShape))
                    }
                }
            }
            Spacer(Modifier.height(56.dp * scale))
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("OK", "0", "Erase")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    row.forEach { value ->
                        TvPinKey(value, scale, !busy && (value != "OK" || pin.length in 4..8),
                            if (value == "5") Modifier.focusRequester(first) else Modifier) {
                            when (value) { "OK" -> submit(); "Erase" -> erase(); else -> digit(value) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp * scale))
            }
            error?.let { Text(it, color = Palette.danger, fontSize = (20 * scale).sp) }
            TextButton(onClick = { forgot = true }) { Text("Forgot PIN?", fontSize = (24 * scale).sp, color = Palette.secondary) }
        }
    }
    if (forgot) AlertDialog(onDismissRequest = { forgot = false }, title = { Text("Profile PIN") },
        text = { Text("PIN recovery is not available in this experiment.") },
        confirmButton = { TextButton(onClick = { forgot = false }) { Text("OK") } })
}

@Composable
private fun TvPinKey(label: String, scale: Float, enabled: Boolean, modifier: Modifier, action: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.size(136.dp * scale, 76.dp * scale).onFocusChanged { focused = it.isFocused }
        .background(if (focused) Palette.text else Palette.raised, RoundedCornerShape(12.dp * scale))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = action),
        contentAlignment = Alignment.Center) {
        val color = if (focused) Palette.background else Palette.text
        if (label == "Erase") Icon(Lucide.Delete, "Erase digit", Modifier.size(26.dp * scale), tint = color)
        else Text(label, fontSize = (if (label == "OK") 22 * scale else 36 * scale).sp, fontWeight = FontWeight.SemiBold, lineHeight = (44 * scale).sp,
            color = color.copy(alpha = if (enabled) 1f else 0.4f))
    }
}
