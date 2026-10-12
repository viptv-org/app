package org.viptv.app.features.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvHomeBackdrop(card: Card?, scale: Float, browsing: Boolean) {
    Crossfade(card, animationSpec = tween(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)), label = "Focused title artwork") { value ->
        AsyncImage(value?.landscape ?: value?.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            alignment = if (browsing) androidx.compose.ui.BiasAlignment(0f, -0.6f) else Alignment.Center)
    }
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
        0f to Palette.background.copy(alpha = 0.86f), 0.36f to Palette.background.copy(alpha = 0.4f), 0.7f to Color.Transparent)))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        0f to Palette.background.copy(alpha = 0.3f), 0.45f to Color.Transparent, 0.8f to Palette.background.copy(alpha = 0.9f), 1f to Palette.background)))
}
