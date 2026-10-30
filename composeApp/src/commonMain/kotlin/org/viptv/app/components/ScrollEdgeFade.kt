package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

internal enum class ScrollEdge { Top, Bottom }

internal val LocalBarsFadeContent = staticCompositionLocalOf { false }

internal val LocalBottomBarFades = staticCompositionLocalOf { false }

internal val ScrollEdgeFadeHeight = 28.dp

// The gradient is measured in pixels: a brush drawn into a shorter rect would still span the whole draw area.
private fun scrim(edge: ScrollEdge, color: Color, fade: Float, height: Float): Brush {
    val solid = (fade / height.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val clear = color.copy(alpha = 0f)
    return when (edge) {
        ScrollEdge.Top -> Brush.verticalGradient(0f to color, solid to clear, endY = height)
        ScrollEdge.Bottom -> Brush.verticalGradient(0f to clear, solid to color, endY = height)
    }
}

@Composable
internal fun ScrollEdgeFade(edge: ScrollEdge, modifier: Modifier = Modifier, fade: Dp = ScrollEdgeFadeHeight,
    height: Dp = fade, color: Color = Palette.background) {
    val density = LocalDensity.current
    val fadePx = with(density) { fade.toPx() }
    val heightPx = with(density) { height.toPx() }
    Box(modifier.fillMaxWidth().height(height).background(scrim(edge, color, fadePx, heightPx)))
}

internal fun Modifier.scrollEdgeFade(edge: ScrollEdge, fade: Dp = ScrollEdgeFadeHeight, height: Dp = fade,
    color: Color = Palette.background): Modifier = drawWithContent {
    drawContent()
    val cover = height.toPx().coerceIn(0f, size.height)
    val gradient = fade.toPx().coerceIn(0f, cover)
    if (gradient <= 0f) return@drawWithContent
    translate(top = if (edge == ScrollEdge.Top) 0f else size.height - cover) {
        drawRect(scrim(edge, color, gradient, cover), size = Size(size.width, cover))
    }
}
