package org.viptv.app.features.home

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp

@Composable
internal fun rememberTvLoadingShimmer(scale: Float): Modifier {
    val transition = rememberInfiniteTransition(label = "TV loading")
    val position = transition.animateFloat(-600f, 600f,
        infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "Placeholder highlight")
    val colors = remember { listOf(Color.White.copy(alpha = .06f), Color.White.copy(alpha = .13f), Color.White.copy(alpha = .06f)) }
    return Modifier.drawBehind {
        val start = (position.value * scale).dp.toPx()
        val width = (1200f * scale).dp.toPx()
        drawRect(Brush.linearGradient(colors, Offset(start, 0f), Offset(start + width, 0f), TileMode.Repeated))
    }
}
