package org.viptv.app.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import org.viptv.app.theme.Palette

@Composable
internal fun FocusOutline(focused: Boolean, radius: Dp, width: Dp, modifier: Modifier) {
    val opacity = animateFloatAsState(if (focused) 1f else 0f, tween(120), label = "Card focus outline")
    Canvas(modifier) {
        val stroke = width.toPx()
        drawRoundRect(Palette.text.copy(alpha = opacity.value), Offset(stroke / 2, stroke / 2),
            Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
            CornerRadius(radius.toPx()), style = Stroke(stroke))
    }
}
