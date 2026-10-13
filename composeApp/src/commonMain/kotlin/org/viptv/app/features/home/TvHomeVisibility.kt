package org.viptv.app.features.home

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun TvHomeVisibility(visible: Boolean, scale: Float, offset: Int,
    duration: Int = 450, retain: Boolean = false, focusEnabled: () -> Boolean = { visible },
    sequentialFade: Boolean = false, content: @Composable () -> Unit) {
    val pixels = with(LocalDensity.current) { (offset.dp * scale).roundToPx() }
    val easing = CubicBezierEasing(.2f, 0f, 0f, 1f)
    val enterDelay = if (sequentialFade) 150 else 0
    val fadeDuration = (duration - enterDelay).coerceAtLeast(150)
    val exitDuration = if (sequentialFade) 150 else duration
    if (retain) {
        val opacity by animateFloatAsState(if (visible) 1f else 0f,
            if (visible) tween(fadeDuration, enterDelay, easing) else tween(exitDuration, easing = easing), label = "Hero visibility")
        val position by animateFloatAsState(if (visible) 0f else pixels.toFloat(), tween(duration, easing = easing), label = "Hero departure")
        Box(Modifier.fillMaxWidth().graphicsLayer { alpha = opacity; translationY = position }
            .focusProperties { this.canFocus = focusEnabled() }) { content() }
        return
    }
    AnimatedVisibility(visible, Modifier.fillMaxWidth().focusProperties { canFocus = visible },
        enter = fadeIn(tween(fadeDuration, enterDelay, easing)) + slideInVertically(tween(duration, easing = easing)) { pixels },
        exit = fadeOut(tween(exitDuration, easing = easing)) + slideOutVertically(tween(duration, easing = easing)) { pixels }) {
        Box(Modifier.fillMaxWidth()) { content() }
    }
}
