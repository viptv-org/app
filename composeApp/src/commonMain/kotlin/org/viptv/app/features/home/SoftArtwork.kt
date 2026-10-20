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

/**
 * A soft backdrop needs one sixteenth of the full-screen blur surface.
 *
 * When the artwork host can be proxied, the blur is asked for *once* at the image
 * service and the runtime blur pass is skipped entirely — that pass is what the TV
 * frame budget actually spends its GPU time on. Hosts the service cannot proxy
 * keep the runtime blur.
 */
@Composable
internal fun SoftArtwork(url: String?, modifier: Modifier, radius: Dp, opacity: Float = 1f) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val targetWidth = with(density) { maxWidth.toPx().toInt() }
        val targetHeight = with(density) { maxHeight.toPx().toInt() }
        // wsrv blur is a Gaussian sigma; the runtime pass blurs the quarter-size
        // surface and upscales 4x, so the sigma that matches it is radius/4 in px.
        val sigma = with(density) { radius.toPx() / 4f }.toInt().coerceIn(1, 200)
        val proxied = org.viptv.app.images.sizedArtwork(url, targetWidth, targetHeight, blur = sigma)
        val preBlurred = proxied != null && proxied.startsWith("https://wsrv.nl/")
        val base = Modifier.size(maxWidth / 4, maxHeight / 4).graphicsLayer {
            scaleX = 4f; scaleY = 4f
            transformOrigin = TransformOrigin(0f, 0f)
            alpha = opacity
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }
        if (preBlurred) BufferedArtwork(proxied, base)
        else BufferedArtwork(url, base.blur(radius / 4))
    }
}
