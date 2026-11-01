package org.viptv.app.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import org.viptv.app.resources.Res
import org.viptv.app.theme.Palette

internal const val LiveEmptyWideArt = "files/live/live-empty-wide.webp"
internal const val LiveEmptyTallArt = "files/live/live-empty-tall.webp"

// The scrim draws in code over the photo: drop it and the light text loses contrast over bright art.
@Composable
internal fun LiveEmptyArtwork(wide: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        AsyncImage(Res.getUri(if (wide) LiveEmptyWideArt else LiveEmptyTallArt), null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.Center)
        Box(Modifier.fillMaxSize().background(if (wide) wideScrim else tallScrim))
    }
}

private val wideScrim = Brush.horizontalGradient(
    0f to Palette.background.copy(alpha = 0.92f),
    0.45f to Palette.background.copy(alpha = 0.6f),
    1f to Palette.background.copy(alpha = 0.18f))

private val tallScrim = Brush.verticalGradient(
    0f to Palette.background.copy(alpha = 0.0f),
    0.4f to Palette.background.copy(alpha = 0.35f),
    1f to Palette.background.copy(alpha = 0.9f))