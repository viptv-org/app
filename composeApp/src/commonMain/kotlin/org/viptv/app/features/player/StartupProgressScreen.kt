package org.viptv.app.features.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.viptv.app.components.TitleLogo
import org.viptv.app.state.StartupProgress
import org.viptv.app.theme.Palette
import org.viptv.engine.wire.Card

private const val DimmedArtAlpha = 0.25f
private const val ShimmerAlpha = 0.14f
private const val ShimmerMillis = 1_600
private const val TitleLineHeight = 1.15f

// The title art at 25 % opacity, with one mask pass dimming everything above the fill line.
@Composable
internal fun StartupProgressScreen(
    progress: StartupProgress,
    title: Card?,
    modifier: Modifier = Modifier,
    artWidth: Dp = 440.dp,
    artMaxHeight: Dp = 320.dp,
    titleSize: TextUnit = 40.sp,
    statusSize: TextUnit = 14.sp,
    reduceMotion: Boolean = false,
) {
    Box(modifier.fillMaxSize().background(Palette.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            val transition = rememberInfiniteTransition(label = "startup shimmer")
            val travel by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(ShimmerMillis, easing = LinearEasing)), label = "startup shimmer travel")
            val shimmering = progress.holding && !reduceMotion
            val fill = progress.fill
            TitleLogo(title?.logo, title?.title.orEmpty(),
                Modifier.width(artWidth).heightIn(max = artMaxHeight)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val edge = size.height * (1f - fill)
                        if (edge > 0f) drawRect(Color.Black.copy(alpha = DimmedArtAlpha), Offset.Zero, Size(size.width, edge), blendMode = BlendMode.DstIn)
                        if (shimmering) {
                            val band = size.height * 0.3f
                            val top = -band + (size.height + band) * travel
                            drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.White.copy(alpha = ShimmerAlpha), 1f to Color.Transparent,
                                startY = top, endY = top + band), Offset(0f, top), Size(size.width, band))
                        }
                    },
                contentAlignment = Alignment.Center, logoModifier = Modifier.fillMaxWidth(), logoAlignment = Alignment.Center,
                logoKeepsItsShapeAtWidth = true) {
                Text(title?.title.orEmpty(), Modifier.fillMaxWidth(), color = Palette.text, fontSize = titleSize,
                    lineHeight = if (titleSize.isSp) (titleSize.value * TitleLineHeight).sp else titleSize,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            Text(progress.status, color = Palette.tertiary, fontSize = statusSize)
        }
    }
}