package org.viptv.app.features.entry

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.Brand
import org.viptv.app.components.TvStartupProgress

@Composable
internal fun TvSplash() {
    val opacity = remember { Animatable(0f) }
    LaunchedEffect(Unit) { opacity.animateTo(1f, tween(600, easing = CubicBezierEasing(0f, 0f, .58f, 1f))) }
    BoxWithConstraints(Modifier.fillMaxSize().semantics { contentDescription = "Starting VIPTV" }) {
        val scale = maxWidth / 1920.dp
        Box(Modifier.align(Alignment.TopCenter).padding(top = 440.dp * scale).height(104.dp * scale).graphicsLayer { alpha = opacity.value }) {
            Brand(88.dp * scale, (104 * scale).sp, 22.dp * scale, (-4.16f * scale).sp, (104 * scale).sp)
        }
        TvStartupProgress(scale, Modifier.align(Alignment.TopCenter).padding(top = 600.dp * scale))
    }
}
