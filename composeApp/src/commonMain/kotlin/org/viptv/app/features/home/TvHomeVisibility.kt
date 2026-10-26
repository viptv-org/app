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

@Composable
internal fun TvHomeVisibility(visible: Boolean, scale: Float, offset: Int,
    duration: Int = 450, retain: Boolean = false, focusEnabled: () -> Boolean = { visible },
    content: @Composable () -> Unit) {
    val pixels = with(LocalDensity.current) { (offset.dp * scale).roundToPx() }
    val easing = CubicBezierEasing(.2f, 0f, 0f, 1f)
    val opacity = remember { Animatable(if (visible) 1f else 0f) }
    var mounted by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            opacity.animateTo(1f, tween(duration, easing = easing))
        } else {
            opacity.animateTo(0f, tween(duration, easing = easing))
            mounted = false
        }
    }
    val position = animateFloatAsState(if (visible) 0f else pixels.toFloat(), tween(duration, easing = easing), label = "Billboard departure")
    if (retain || mounted || visible) {
        Box(Modifier.fillMaxWidth().graphicsLayer { alpha = opacity.value; translationY = position.value }
            // A retained-but-hidden plane (the hero while browsing) must not
            // rasterize its subtree every frame: alpha 0 leaves every pixel
            // untouched, so skipping the draw is pixel-identical. Read in the
            // draw phase, so the gate itself never recomposes.
            .drawWithContent { if (opacity.value > 0f) drawContent() }
            // Hidden content must not stay in the accessibility tree or the
            // focus graph. The visible case must NOT install a focus boundary:
            // `focusProperties` on this non-focusable container made Up from the
            // hero button a dead end, so the remote could not reach the nav.
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics { }.focusProperties { canFocus = false })) { content() }
    }
}
