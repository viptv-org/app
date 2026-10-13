package org.viptv.app.components

import androidx.compose.animation.core.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
internal fun TvLoadingIndicator(modifier: Modifier, scale: Float) {
    val rotation by rememberInfiniteTransition(label = "TV buffering").animateFloat(
        initialValue = -45f, targetValue = 315f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)), label = "spinner rotation")
    CircularProgressIndicator(progress = { .25f },
        modifier = modifier.graphicsLayer { rotationZ = rotation }.clearAndSetSemantics {
            contentDescription = "Loading"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }, color = Color.White, strokeWidth = 5.dp * scale,
        trackColor = Color.White.copy(alpha = .18f), strokeCap = StrokeCap.Butt, gapSize = 0.dp)
}
