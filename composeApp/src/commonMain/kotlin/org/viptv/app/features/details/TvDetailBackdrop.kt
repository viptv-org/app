package org.viptv.app.features.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import org.viptv.app.components.ART_ATTEMPTS
import org.viptv.app.components.ArtFailure
import org.viptv.app.components.retryAfterFailure
import org.viptv.app.features.home.BufferedArtwork
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvDetailBackdrop(card: Card?, scale: Float, scrollPixels: () -> Float) {
    Box(Modifier.fillMaxSize().background(Palette.background)) {
        Box(Modifier.fillMaxWidth().height(1080.dp * scale).graphicsLayer {
            translationY = -scrollPixels()
            compositingStrategy = CompositingStrategy.Offscreen
        }) {
            // A card's landscape can be a dead link, so fall back to the poster once the landscape's retries run out.
            val primary = card?.landscape ?: card?.poster
            val fallback = card?.poster?.takeIf { it != primary }
            val art = remember(card?.identity) { ArtFailure() }
            val attempt = art.retryAfterFailure()
            key(attempt) { BufferedArtwork(if (fallback != null && art.attempts >= ART_ATTEMPTS) fallback else primary,
                Modifier.fillMaxSize(),
                if (card?.identity?.kind == "series") BiasAlignment(.2f, -.6f) else BiasAlignment(0f, -.4f),
                onReady = { art.failed = false }, onError = { art.failed = true }) }
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
