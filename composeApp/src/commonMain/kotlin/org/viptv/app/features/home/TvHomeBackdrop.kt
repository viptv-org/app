package org.viptv.app.features.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

@Composable
internal fun TvHomeBackdrop(card: Card?, browsing: Boolean, portrait: Boolean = false, hero: Card? = card, heroReady: (String?) -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background)) {
        val scale = maxWidth / 1920.dp
        val motion = animateFloatAsState(if (browsing) 1f else 0f, tween(450, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)), label = "Hero artwork departure")
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = 1f - motion.value; translationY = -80.dp.toPx() * scale * motion.value
            clip = true; compositingStrategy = CompositingStrategy.Auto
        }) {
            BufferedArtwork(hero?.landscape ?: hero?.poster, Modifier.fillMaxSize(), onReady = heroReady)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Palette.background.copy(alpha = .86f), .36f to Palette.background.copy(alpha = .4f), .7f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Palette.background.copy(alpha = .3f), .45f to Color.Transparent, .8f to Palette.background.copy(alpha = .9f), 1f to Palette.background)))
        }
        Box(Modifier.fillMaxWidth().height(620.dp * scale).graphicsLayer {
            alpha = motion.value; clip = true; compositingStrategy = CompositingStrategy.Auto
        }.background(Palette.background)) {
            if (portrait) {
                SoftArtwork(card?.poster, Modifier.fillMaxWidth().height(620.dp * scale)
                    .graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }, 70.dp * scale, .45f)
                BufferedArtwork(card?.poster, Modifier.offset(1520.dp * scale, 96.dp * scale).size(280.dp * scale, 420.dp * scale).clip(RoundedCornerShape(12.dp * scale)))
            } else BufferedArtwork(card?.landscape ?: card?.poster, Modifier.offset(760.dp * scale, 0.dp).size(1160.dp * scale, 620.dp * scale), BiasAlignment(.2f, -.5f))
        // Masks belong to the browsing plane, outside the individual artwork fade.
        if (!portrait) Box(Modifier.offset(760.dp * scale, 0.dp).size(560.dp * scale, 620.dp * scale)
            .background(Brush.horizontalGradient(listOf(Palette.background, Palette.background.copy(alpha = 0f)))))
        Box(Modifier.offset(0.dp, 380.dp * scale).fillMaxWidth().height(240.dp * scale)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.background))))
        }
    }
}
