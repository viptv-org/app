package org.viptv.app.features.home

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
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
    duration: Int = 450, content: @Composable () -> Unit) {
    val pixels = with(LocalDensity.current) { (offset.dp * scale).roundToPx() }
    val easing = CubicBezierEasing(.2f, 0f, 0f, 1f)
    AnimatedVisibility(visible, Modifier.fillMaxWidth().focusProperties { canFocus = visible },
        enter = fadeIn(tween(duration, easing = easing)) + slideInVertically(tween(duration, easing = easing)) { pixels },
        exit = fadeOut(tween(duration, easing = easing)) + slideOutVertically(tween(duration, easing = easing)) { pixels }) {
        Box(Modifier.fillMaxWidth()) { content() }
    }
}
