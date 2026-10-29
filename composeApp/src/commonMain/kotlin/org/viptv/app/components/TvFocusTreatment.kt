package org.viptv.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

internal fun Modifier.tvFocusOutline(focused: Boolean, radius: Dp, scale: Float = 1f,
    width: Dp = 4.dp, darkGap: Dp = 0.dp): Modifier = if (!focused) this else drawWithContent {
    drawContent()
    val w = width.toPx() * scale
    val g = darkGap.toPx() * scale
    val r = radius.toPx()
    if (g > 0f) {
        drawRoundRect(Color.Black.copy(alpha = .65f), Offset(-g / 2f, -g / 2f),
            Size(size.width + g, size.height + g), CornerRadius(r + g / 2f), style = Stroke(g))
    }
    // The ring is drawn outside the control's own bounds, so focus never re-measures the layout.
    drawRoundRect(Color.White, Offset(-g - w / 2f, -g - w / 2f),
        Size(size.width + 2 * g + w, size.height + 2 * g + w), CornerRadius(r + g + w / 2f), style = Stroke(w))
}

// Material's ripple would paint a second, differently shaped focus layer under this treatment.
private val OwnFocusRipple = RippleConfiguration(rippleAlpha = RippleAlpha(
    draggedAlpha = .16f, focusedAlpha = 0f, hoveredAlpha = .08f, pressedAlpha = .1f))

@Composable
internal fun OwnFocusTreatment(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalRippleConfiguration provides OwnFocusRipple, content = content)

internal fun tvRingReach(scale: Float): Dp = 8.dp * scale

// Scroll containers clip the outside ring, so the container grows instead and the content takes the same padding.
internal fun Modifier.tvRingRoom(room: Dp, horizontal: Boolean = true, vertical: Boolean = true): Modifier =
    layout { measurable, constraints ->
        val x = if (horizontal) room.roundToPx() else 0
        val y = if (vertical) room.roundToPx() else 0
        val placeable = measurable.measure(constraints.offset(2 * x, 2 * y))
        layout((placeable.width - 2 * x).coerceIn(constraints.minWidth, constraints.maxWidth),
            (placeable.height - 2 * y).coerceIn(constraints.minHeight, constraints.maxHeight)) { placeable.place(-x, -y) }
    }

// Card focus is an inset ring: the board never grows a card on focus.
internal fun Modifier.tvCardFocusOutline(focused: Boolean, radius: Dp, scale: Float = 1f,
    width: Dp = 3.dp): Modifier = if (!focused) this else drawWithContent {
    drawContent()
    val w = width.toPx() * scale
    drawRoundRect(Color.White, Offset(w / 2f, w / 2f),
        Size((size.width - w).coerceAtLeast(0f), (size.height - w).coerceAtLeast(0f)),
        CornerRadius((radius.toPx() - w / 2f).coerceAtLeast(0f)), style = Stroke(w))
}

@Composable
internal fun TvFocusSurface(focused: Boolean, radius: Dp, scale: Float, modifier: Modifier = Modifier,
    unfocused: Color = Palette.text.copy(alpha = .06f), content: @Composable () -> Unit) {
    Box(modifier
        .tvFocusOutline(focused, radius, scale)
        .background(if (focused) Palette.text else unfocused, androidx.compose.foundation.shape.RoundedCornerShape(radius))) {
        content()
    }
}
