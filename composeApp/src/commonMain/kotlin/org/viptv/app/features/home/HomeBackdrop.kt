package org.viptv.app.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.viptv.app.theme.Palette

@Composable
internal fun BoxScope.HomeBackdrop(artwork: String) {
    Box(Modifier.matchParentSize().clipToBounds()) {
        val blurred = org.viptv.app.images.blurredArtwork(artwork, 1280, 720, blur = 12)
        AsyncImage(blurred ?: artwork.takeIf { it.isNotBlank() }, null, Modifier.matchParentSize().let { base ->
            if (blurred != null) base.graphicsLayer { alpha = 0.45f; scaleX = 1.25f; scaleY = 1.25f }
            else base.graphicsLayer { alpha = 0.45f; scaleX = 1.25f; scaleY = 1.25f }.blur(60.dp, BlurredEdgeTreatment.Unbounded)
        }, contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Palette.background.copy(alpha = 0.15f), 0.6f to Palette.background.copy(alpha = 0.65f), 1f to Palette.background)))
    }
}
