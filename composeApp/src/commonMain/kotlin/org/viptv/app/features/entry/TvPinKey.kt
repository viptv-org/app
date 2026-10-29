package org.viptv.app.features.entry

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.tvFocusOutline
import org.viptv.app.theme.Palette
import org.viptv.app.components.pointerClickable

@Composable
internal fun TvPinKey(label: String, scale: Float, enabled: Boolean, requester: FocusRequester?, width: Int = 136,
    numberSize: Int = 34, ring: Boolean = true, action: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fallback = remember { FocusRequester() }
    val target = requester ?: fallback
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val shape = RoundedCornerShape(10.dp * scale)
    val light = enabled && focused
    val fill by animateColorAsState(when { enabled && pressed -> Palette.body; light -> Palette.text; else -> Color.White.copy(alpha = .09f) },
        tween(150), label = "PIN key fill")
    val ink by animateColorAsState(if (light || enabled && pressed) Palette.background else Palette.text,
        tween(150), label = "PIN key content")
    Box(Modifier.size(width.dp * scale, 76.dp * scale).focusRequester(target)
        .onFocusChanged { if (focused != it.isFocused) focused = it.isFocused }
        // Tv3Pin keys paint focus inside their own cell: the outside 4 px outline would grow a 136 px key to 144 px and eat the 12 px pitch.
        .tvFocusOutline(light && ring, 10.dp * scale, scale)
        .graphicsLayer { alpha = if (enabled) 1f else .38f; this.shape = shape; shadowElevation = if (light && ring) 24.dp.toPx() * scale else 0f }
        .clip(shape).background(fill)
        .pointerClickable(enabled = enabled, interactionSource = interactions, indication = null, onClick = action, shape = shape),
        contentAlignment = Alignment.Center) {
        if (label == "Erase") Icon(Lucide.Delete, "Erase digit", Modifier.size(28.dp * scale), ink)
        else Text(label, color = ink.copy(alpha = if (enabled) 1f else .4f), fontSize = (if (label == "OK") 22 * scale else numberSize * scale).sp,
            lineHeight = (40 * scale).sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun TvPinDigits(pin: String, scale: Float, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp * scale)) {
        repeat(if (pin.length > 4) 8 else 4) { index ->
            val shape = RoundedCornerShape(14.dp * scale)
            Box(Modifier.size(88.dp * scale, 104.dp * scale).background(Color.White.copy(alpha = .08f), shape)
                .border(2.dp * scale, if (index == pin.length) Palette.text else Color.White.copy(alpha = .14f), shape)
                .semantics { contentDescription = "PIN digit ${index + 1}, ${if (index < pin.length) "filled" else "empty"}" },
                contentAlignment = Alignment.Center) {
                if (index < pin.length) Box(Modifier.size(22.dp * scale).background(Palette.text, CircleShape))
            }
        }
    }
}

@Composable
internal fun TvPinKeys(pin: String, scale: Float, busy: Boolean, first: FocusRequester?, digit: (String) -> Unit,
    erase: () -> Unit, submit: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("OK", "0", "Erase")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                row.forEach { value ->
                    if (value == "OK" && pin.length < 4) Spacer(Modifier.size(136.dp * scale, 76.dp * scale))
                    else TvPinKey(value, scale, !busy, if (value == "5") first else null, ring = false) {
                        when (value) { "OK" -> submit(); "Erase" -> erase(); else -> digit(value) }
                    }
                }
            }
        }
    }
}
