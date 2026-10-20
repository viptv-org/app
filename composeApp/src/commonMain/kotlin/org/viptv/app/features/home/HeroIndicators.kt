package org.viptv.app.features.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

@Composable
internal fun HeroIndicators(count: Int, selected: Int, progress: HeroProgress, modifier: Modifier = Modifier, scale: Float = 1f) {
    if (count < 2) return
    val selectedWidth = 48.dp * scale
    // One progress step per pixel of fill: the bar redraws only when it visibly moves.
    val fillPixels = with(LocalDensity.current) { selectedWidth.roundToPx() }
    SideEffect { progress.steps = fillPixels }
    // The board exposes the indicator strip as "Featured 1 of 5"; the visible
    // text stays hidden, but the remote/reader gets the position.
    Row(modifier.semantics { contentDescription = "Featured ${selected + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        repeat(count) { index ->
            Canvas(Modifier.width(if (index == selected) selectedWidth else 16.dp * scale).height(4.dp * scale)) {
                val radius = CornerRadius(2.dp.toPx() * scale)
                drawRoundRect(Palette.text.copy(alpha = .35f), cornerRadius = radius)
                if (index == selected) drawRoundRect(Palette.text,
                    size = Size(size.width * progress.fraction.coerceIn(0f, 1f), size.height), cornerRadius = radius)
            }
        }
    }
}
