package org.viptv.app.features.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun TvHomeVisibility(visible: Boolean, scale: Float, offset: Int,
    duration: Int = 450, retain: Boolean = false, focusEnabled: () -> Boolean = { visible },
    sequentialFade: Boolean = false, content: @Composable () -> Unit) {
    val pixels = with(LocalDensity.current) { (offset.dp * scale).roundToPx() }
    val easing = CubicBezierEasing(.2f, 0f, 0f, 1f)
    val enterDelay = if (sequentialFade) 150 else 0
    val fadeDuration = (duration - enterDelay).coerceAtLeast(150)
    val exitDuration = if (sequentialFade) 150 else duration
    val opacity = remember { Animatable(if (visible) 1f else 0f) }
    var mounted by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            // A reversed exit must not retain its partial opacity during the enter delay.
            if (sequentialFade && opacity.value < 1f) {
                opacity.snapTo(0f)
                delay(enterDelay.toLong())
            }
            opacity.animateTo(1f, tween(fadeDuration, easing = easing))
        } else {
            opacity.animateTo(0f, tween(exitDuration, easing = easing))
            mounted = false
        }
    }
    val position = animateFloatAsState(if (visible) 0f else pixels.toFloat(), tween(duration, easing = easing), label = "Billboard departure")
    if (retain || mounted || visible) {
        Box(Modifier.fillMaxWidth().graphicsLayer { alpha = opacity.value; translationY = position.value }
            .focusProperties { this.canFocus = focusEnabled() }) { content() }
    }
}
