package org.viptv.app.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

@Composable
internal fun SoftArtwork(url: String?, modifier: Modifier, radius: Dp, opacity: Float = 1f) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val targetWidth = with(density) { maxWidth.toPx().toInt() }
        val targetHeight = with(density) { maxHeight.toPx().toInt() }
        // wsrv's blur is a Gaussian sigma; the runtime pass blurs a quarter-size surface and upscales 4x, hence radius/4 px.
        val sigma = with(density) { radius.toPx() / 4f }.toInt().coerceIn(1, 200)
        val preBlurred = org.viptv.app.images.blurredArtwork(url, targetWidth, targetHeight, blur = sigma)
        val base = Modifier.size(maxWidth / 4, maxHeight / 4).graphicsLayer {
            scaleX = 4f; scaleY = 4f
            transformOrigin = TransformOrigin(0f, 0f)
            alpha = opacity
            // Apply opacity to the composed quarter-size dissolve, not each bitmap, which brightens overlapping images.
            compositingStrategy = CompositingStrategy.Auto
        }
        if (preBlurred != null) BufferedArtwork(preBlurred, base)
        else BufferedArtwork(url, base.blur(radius / 4))
    }
}
