package org.viptv.app.features.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import org.viptv.app.components.motionSpec

@Composable
internal fun TvHomeVisibility(visible: Boolean, scale: Float, offset: Int,
    duration: Int = 450, retain: Boolean = false, focusEnabled: () -> Boolean = { visible },
    content: @Composable () -> Unit) {
    val pixels = with(LocalDensity.current) { (offset.dp * scale).roundToPx() }
    val easing = CubicBezierEasing(.2f, 0f, 0f, 1f)
    val fade = motionSpec<Float>(tween(duration, easing = easing))
    val opacity = remember { Animatable(if (visible) 1f else 0f) }
    var mounted by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            opacity.animateTo(1f, fade)
        } else {
            opacity.animateTo(0f, fade)
            mounted = false
        }
    }
    val position = animateFloatAsState(if (visible) 0f else pixels.toFloat(), fade, label = "Billboard departure")
    if (retain || mounted || visible) {
        Box(Modifier.fillMaxWidth().graphicsLayer { alpha = opacity.value; translationY = position.value }
            // A retained but hidden plane is skipped in the draw phase: alpha 0 leaves every pixel untouched, so the draw is pixel-identical.
            .drawWithContent { if (opacity.value > 0f) drawContent() }
            // A visible plane installs no focus boundary: focusProperties on this non-focusable container dead-ends Up from the hero button.
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics { }.focusProperties { canFocus = false })) { content() }
    }
}
