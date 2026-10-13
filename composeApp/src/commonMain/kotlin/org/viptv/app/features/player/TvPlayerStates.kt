package org.viptv.app.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import org.viptv.app.components.TvAction

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
