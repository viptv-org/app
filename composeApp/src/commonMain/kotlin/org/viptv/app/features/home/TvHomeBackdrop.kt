package org.viptv.app.features.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card
import androidx.compose.ui.unit.dp
import androidx.compose.ui.BiasAlignment

@Composable
internal fun TvHomeBackdrop(card: Card?, browsing: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.background)) {
        val scale = maxWidth / 1920.dp
        Crossfade(card to browsing, animationSpec = tween(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)), label = "Focused title artwork") { (value, rows) ->
            if (rows) {
                AsyncImage(value?.landscape, null, Modifier.offset(760.dp * scale, 0.dp).size(1160.dp * scale, 620.dp * scale),
                    contentScale = ContentScale.Crop, alignment = BiasAlignment(.2f, -.5f))
                Box(Modifier.offset(760.dp * scale, 0.dp).size(560.dp * scale, 620.dp * scale)
                    .background(Brush.horizontalGradient(listOf(Palette.background, Color.Transparent))))
                Box(Modifier.offset(0.dp, 380.dp * scale).fillMaxWidth().height(241.dp * scale)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.background))))
            } else {
                AsyncImage(value?.landscape, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.Center)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    0f to Palette.background.copy(alpha = .86f), .36f to Palette.background.copy(alpha = .4f), .7f to Color.Transparent)))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to Palette.background.copy(alpha = .3f), .45f to Color.Transparent, .8f to Palette.background.copy(alpha = .9f), 1f to Palette.background)))
            }
        }
    }
}
