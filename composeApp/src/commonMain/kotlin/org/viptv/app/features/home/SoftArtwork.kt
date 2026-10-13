package org.viptv.app.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/** A soft backdrop needs one sixteenth of the full-screen blur surface. */
@Composable
internal fun SoftArtwork(url: String?, modifier: Modifier, radius: Dp, opacity: Float = 1f) {
    BoxWithConstraints(modifier) {
        BufferedArtwork(url, Modifier.size(maxWidth / 4, maxHeight / 4).graphicsLayer {
            scaleX = 4f; scaleY = 4f
            transformOrigin = TransformOrigin(0f, 0f)
            alpha = opacity
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }.blur(radius / 4))
    }
}
