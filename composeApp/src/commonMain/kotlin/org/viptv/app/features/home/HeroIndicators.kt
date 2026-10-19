package org.viptv.app.features.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

@Composable
internal fun HeroIndicators(count: Int, selected: Int, progress: () -> Float, modifier: Modifier = Modifier, scale: Float = 1f) {
    if (count < 2) return
    // The board exposes the indicator strip as "Featured 1 of 5"; the visible
    // text stays hidden, but the remote/reader gets the position.
    Row(modifier.semantics { contentDescription = "Featured ${selected + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
        repeat(count) { index ->
            Canvas(Modifier.width((if (index == selected) 48 else 16).dp * scale).height(4.dp * scale)) {
                val radius = CornerRadius(2.dp.toPx() * scale)
                drawRoundRect(Palette.text.copy(alpha = .35f), cornerRadius = radius)
                if (index == selected) drawRoundRect(Palette.text,
                    size = Size(size.width * progress().coerceIn(0f, 1f), size.height), cornerRadius = radius)
            }
        }
    }
}
