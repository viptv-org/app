package org.viptv.app.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction
import org.viptv.app.components.tvBackdropBlur
import org.viptv.app.theme.Palette

@Composable
internal fun BoxScope.TvSkipPrompt(skip: TvSkipRange, position: Long, scale: Float, activate: () -> Unit) {
    val initial = remember { FocusRequester() }
    val arrival = remember(skip) { Animatable(0f) }
    val remaining = ((skip.endMillis - position).toDouble() / (skip.endMillis - skip.startMillis).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    val rail by animateFloatAsState(remaining, tween(200, easing = LinearEasing), label = "intro remaining")
    LaunchedEffect(skip) { arrival.animateTo(1f, tween(300, easing = CubicBezierEasing(.2f, 0f, 0f, 1f))) }
    LaunchedEffect(skip) { initial.requestFocus() }
    Box(Modifier.align(Alignment.BottomEnd).padding(end = 96.dp * scale, bottom = 112.dp * scale).width(280.dp * scale).height(72.dp * scale)
        .graphicsLayer { alpha = arrival.value; translationY = 16.dp.toPx() * scale * (1f - arrival.value) }
        .clip(RoundedCornerShape(12.dp * scale))) {
        TvAction(Modifier.fillMaxSize(), scale, primary = true, requester = initial, activate = activate) {
            Icon(Lucide.SkipForward, null, Modifier.size(26.dp * scale), it); Spacer(Modifier.width(12.dp * scale))
            Text(skip.label, color = it, fontSize = (26 * scale).sp, lineHeight = (37.7f * scale).sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(rail).height(4.dp * scale).background(Color(0xFF111113).copy(alpha = .35f)))
    }
}

/**
 * A secondary player control on the board's translucent-blur surface (rule R5).
 *
 * The blurred video is a layer *behind* [TvAction], not a modifier in its chain:
 * the focus ring is painted outside the control's own bounds, so any clip in
 * front of it would cut the ring, and [TvAction] still owns the focus/press fill
 * that covers the blur. Geometry, the focus requester and the column `down`
 * target stay exactly where the caller put them.
 */
@Composable
internal fun TvGlassAction(modifier: Modifier, scale: Float,
    requester: FocusRequester? = null, down: FocusRequester? = null, cornerRadius: Dp = 12.dp * scale,
    contentPadding: PaddingValues = PaddingValues(0.dp), background: Color = Palette.text.copy(alpha = .16f),
    activate: () -> Unit, content: @Composable RowScope.(Color) -> Unit) {
    Box {
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(cornerRadius)).tvBackdropBlur(24.dp * scale))
        TvAction(down?.let { target -> modifier.focusProperties { this.down = target } } ?: modifier, scale,
            requester = requester, activate = activate, contentPadding = contentPadding,
            background = background, cornerRadius = cornerRadius, content = content)
    }
}
