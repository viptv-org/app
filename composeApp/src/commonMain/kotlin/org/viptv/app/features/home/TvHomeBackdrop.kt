package org.viptv.app.features.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

private const val ScrimWidth = .7f

@Composable
internal fun TvHomeBackdrop(card: Card?, browsing: Boolean, portrait: Boolean = false, hero: Card? = card, heroReady: (String?) -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background)) {
        val scale = maxWidth / 1920.dp
        var lastPortrait by remember { mutableStateOf<Card?>(null) }
        var lastLandscape by remember { mutableStateOf<Card?>(null) }
        SideEffect { if (portrait) lastPortrait = card else lastLandscape = card }
        val portraitCard = if (portrait) card else lastPortrait
        val landscapeCard = if (portrait) lastLandscape else card
        val format = animateFloatAsState(if (portrait) 1f else 0f, tween(180), label = "Artwork format")
        val motion = animateFloatAsState(if (browsing) 1f else 0f, tween(220, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)), label = "Hero artwork departure")
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = 1f; translationY = -12.dp.toPx() * scale * motion.value
            clip = true; compositingStrategy = CompositingStrategy.Auto
        // Once the browse plane has fully arrived, the hero plane is invisible:
        // skipping its draw (fullscreen art + two fullscreen scrims) is
        // pixel-identical to drawing it at alpha 0, and reads in the draw phase.
        }.drawWithContent { if (motion.value < 1f) drawContent() }) {
            BufferedArtwork(hero?.landscape ?: hero?.poster, Modifier.fillMaxSize(), onReady = heroReady)
            // The scrim is transparent past 70 % of the width: draw only that part, with the
            // stops rescaled to the narrower box (same pixels, 30 % less fill per frame).
            Box(Modifier.fillMaxHeight().fillMaxWidth(ScrimWidth).background(Brush.horizontalGradient(0f to Palette.background.copy(alpha = .86f), (.36f / ScrimWidth) to Palette.background.copy(alpha = .4f), 1f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Palette.background.copy(alpha = .3f), .45f to Color.Transparent, .8f to Palette.background.copy(alpha = .9f), 1f to Palette.background)))
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = motion.value; clip = true; compositingStrategy = CompositingStrategy.Auto
        // While the hero is settled, the whole browsing plane sits at alpha 0;
        // skip its draw entirely (pixel-identical to drawing it).
        }.drawWithContent { if (motion.value > 0f) drawContent() }.background(Palette.background)) {
            // Keep both format planes mounted and retain their last artwork. The
            // portrait plane dissolves over an opaque landscape base in either
            // direction, without exposing the background or recreating painters.
            Box(Modifier.fillMaxWidth().height(620.dp * scale).drawWithContent { if (format.value < 1f) drawContent() }) {
                BufferedArtwork(landscapeCard?.landscape ?: landscapeCard?.poster, Modifier.offset(760.dp * scale, 0.dp).size(1160.dp * scale, 620.dp * scale), BiasAlignment(.2f, -.5f))
                Box(Modifier.offset(760.dp * scale, 0.dp).size(560.dp * scale, 620.dp * scale)
                    .background(Brush.horizontalGradient(listOf(Palette.background, Palette.background.copy(alpha = 0f)))))
            }
            Box(Modifier.fillMaxWidth().height(620.dp * scale).graphicsLayer { alpha = format.value; clip = true }
                .drawWithContent { if (format.value > 0f) drawContent() }.background(Palette.background)) {
                SoftArtwork(portraitCard?.poster, Modifier.fillMaxWidth().height(620.dp * scale)
                    .graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }, 70.dp * scale, .45f)
                BufferedArtwork(portraitCard?.poster, Modifier.offset(1520.dp * scale, 96.dp * scale).size(280.dp * scale, 420.dp * scale).clip(RoundedCornerShape(12.dp * scale)))
            }
        Box(Modifier.offset(0.dp, 380.dp * scale).fillMaxWidth().height(240.dp * scale)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.background))))
        }
    }
}
