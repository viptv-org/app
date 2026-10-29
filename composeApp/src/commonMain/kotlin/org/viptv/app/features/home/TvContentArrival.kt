package org.viptv.app.features.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import org.viptv.app.components.motionSpec

@Composable
internal fun rememberTvContentArrival(ready: Boolean): Modifier {
    val opacity = remember { Animatable(if (ready) 1f else 0f) }
    val arrival = motionSpec<Float>(tween(200, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)))
    LaunchedEffect(ready) {
        if (ready) opacity.animateTo(1f, arrival)
        else opacity.snapTo(0f)
    }
    return Modifier.graphicsLayer { alpha = if (ready) opacity.value else 1f }
}
