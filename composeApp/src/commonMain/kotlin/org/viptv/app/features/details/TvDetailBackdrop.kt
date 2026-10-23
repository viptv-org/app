package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import org.viptv.app.features.home.BufferedArtwork
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

/** One sharp cached surface follows absolute scrolling across lazy-item boundaries. */
@Composable
internal fun TvDetailBackdrop(card: Card?, scale: Float, scrollPixels: () -> Float) {
    Box(Modifier.fillMaxSize().background(Palette.background)) {
        Box(Modifier.fillMaxWidth().height(1080.dp * scale).graphicsLayer {
            translationY = -scrollPixels()
            compositingStrategy = CompositingStrategy.Offscreen
        }) {
            // The card's landscape can be a dead link (metahub 307s land on a 404):
            // fall back to the poster when the backdrop's first choice fails.
            var artFailed by remember(card?.identity) { mutableStateOf(false) }
            val art = if (artFailed) card?.poster ?: card?.landscape else card?.landscape ?: card?.poster
            BufferedArtwork(art, Modifier.fillMaxSize(),
                if (card?.identity?.kind == "series") BiasAlignment(.2f, -.6f) else BiasAlignment(0f, -.4f),
                onError = { artFailed = true })
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                0f to Palette.background.copy(alpha = .92f),
                .36f to Palette.background.copy(alpha = if (card?.identity?.kind == "series") .7f else .65f),
                .64f to Palette.background.copy(alpha = .05f))))
            Box(Modifier.offset(y = 600.dp * scale).fillMaxWidth().height(480.dp * scale)
                .background(Brush.verticalGradient(0f to Color.Transparent,
                    .6f to Palette.background.copy(alpha = .95f), 1f to Palette.background)))
        }
    }
}
