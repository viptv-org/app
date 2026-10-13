package org.viptv.app.features.entry

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun tvProfileArrival(key: String, index: Int, scale: Float,
    arrivals: MutableMap<String, Animatable<Float, AnimationVector1D>>): Modifier {
    val progress = arrivals.getOrPut(key) { Animatable(0f) }
    val distance = with(LocalDensity.current) { (16.dp * scale).toPx() }
    LaunchedEffect(progress) {
        if (progress.value < 1f) progress.animateTo(1f, tween(450,
            delayMillis = if (progress.value == 0f) index * 60 else 0, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)))
    }
    return Modifier.graphicsLayer { alpha = progress.value; translationY = distance * (1f - progress.value) }
}
