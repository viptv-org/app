package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvDetailBackdrop(card: Card?, scale: Float, expanded: Boolean, offset: Int) {
    Crossfade(expanded, Modifier.fillMaxSize(), animationSpec = tween(350), label = "Detail backdrop") { blurred ->
    if (blurred) {
        org.viptv.app.features.home.SoftArtwork(card?.landscape ?: card?.poster,
            Modifier.fillMaxSize().graphicsLayer { scaleX = 1.2f; scaleY = 1.4f }, 90.dp * scale, .35f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .65f to Palette.background.copy(alpha = .85f), 1f to Palette.background)))
    } else Box(Modifier.offset(y = (-offset).dp).fillMaxWidth().height(1080.dp * scale)) {
        AsyncImage(card?.landscape ?: card?.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            alignment = if (card?.identity?.kind == "series") BiasAlignment(0.2f, -0.6f) else BiasAlignment(0f, -0.4f))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to Palette.background.copy(alpha = 0.92f), 0.36f to Palette.background.copy(alpha = if (card?.identity?.kind == "series") 0.7f else 0.65f),
            0.64f to Palette.background.copy(alpha = 0.05f))))
        Box(Modifier.offset(y = 600.dp * scale).fillMaxWidth().height(480.dp * scale).background(Brush.verticalGradient(
            0f to Color.Transparent, 0.6f to Palette.background.copy(alpha = 0.95f), 1f to Palette.background)))
    }
    }
}
