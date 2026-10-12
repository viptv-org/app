package org.viptv.app.features.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
internal fun rememberTvContentArrival(ready: Boolean): Modifier {
    // Already-loaded lazy items stay visible when they re-enter composition.
    val opacity = remember { Animatable(if (ready) 1f else 0f) }
    LaunchedEffect(ready) {
        if (ready) opacity.animateTo(1f, tween(200, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)))
        else opacity.snapTo(0f)
    }
    return Modifier.graphicsLayer { alpha = if (ready) opacity.value else 1f }
}
