package org.viptv.app.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

@Composable
internal fun TvStartupProgress(scale: Float, modifier: Modifier = Modifier) {
    val position = rememberInfiniteTransition(label = "Startup progress").animateFloat(-1f, 2.6f,
        infiniteRepeatable(tween(1200, easing = CubicBezierEasing(.4f, 0f, .2f, 1f))), label = "Indicator movement")
    Box(modifier.size(240.dp * scale, 4.dp * scale).clip(RoundedCornerShape(2.dp * scale)).background(Color.White.copy(alpha = .12f))) {
        Box(Modifier.width(96.dp * scale).fillMaxHeight().graphicsLayer { translationX = position.value * size.width }
            .background(Palette.text, RoundedCornerShape(2.dp * scale)))
    }
}
