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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.viptv.app.theme.Palette

/**
 * The board's focus treatment: the focused control is *filled* with the light
 * surface and gets a 4 px white outline outside its own bounds plus a soft
 * shadow. It never changes the control's size or position, so a focus move does
 * not re-measure the row it lives in (PERF-02) and neighbouring controls never
 * shift.
 *
 * The outline is drawn outside the layout bounds on purpose; Compose does not
 * clip a draw scope to the node bounds unless a parent asks it to, so this stays
 * a paint-only effect. The inner dark ring is the board's `0 0 0 3px #0B0B0C`
 * shadow: it keeps a filled control legible on a light page.
 */
internal fun Modifier.tvFocusOutline(focused: Boolean, radius: Dp, scale: Float = 1f,
    width: Dp = 4.dp, darkGap: Dp = 0.dp): Modifier = if (!focused) this else drawWithContent {
    drawContent()
    val w = width.toPx() * scale
    val g = darkGap.toPx() * scale
    val r = radius.toPx() * scale
    if (g > 0f) {
        // The sidebar's double ring: 3 px dark, then 3 px white (board
        // `0 0 0 3px #0B0B0C, 0 0 0 6px #F4F2EE`).
        drawRoundRect(Color.Black.copy(alpha = .65f), Offset(-g / 2f, -g / 2f),
            Size(size.width + g, size.height + g), CornerRadius(r + g / 2f), style = Stroke(g))
    }
    // TVU-16: a 4 px white outline outside the control's own bounds. Drawing it
    // outside keeps the layout unchanged (no re-measure on focus).
    drawRoundRect(Color.White, Offset(-g - w / 2f, -g - w / 2f),
        Size(size.width + 2 * g + w, size.height + 2 * g + w), CornerRadius(r + g + w / 2f), style = Stroke(w))
}

/**
 * Material's ripple paints its own focus state layer on every default-indication
 * `clickable` and on Material buttons. Under a control that already draws the
 * board's focus treatment that layer reads as a second, differently shaped ring
 * (the IconButton's circle, an unclipped column's rectangle). Press and hover
 * feedback keep Material's alphas.
 */
private val OwnFocusRipple = RippleConfiguration(rippleAlpha = RippleAlpha(
    draggedAlpha = .16f, focusedAlpha = 0f, hoveredAlpha = .08f, pressedAlpha = .1f))

@Composable
internal fun OwnFocusTreatment(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalRippleConfiguration provides OwnFocusRipple, content = content)

/** Card focus is an *inset* ring: the board never grows a card on focus. */
internal fun Modifier.tvCardFocusOutline(focused: Boolean, radius: Dp, scale: Float = 1f,
    width: Dp = 3.dp): Modifier = if (!focused) this else drawWithContent {
    drawContent()
    val w = width.toPx() * scale
    drawRoundRect(Color.White, Offset(w / 2f, w / 2f),
        Size((size.width - w).coerceAtLeast(0f), (size.height - w).coerceAtLeast(0f)),
        CornerRadius(radius.toPx() * scale), style = Stroke(w))
}

/**
 * A surface whose fill flips to the light token when focused. This is the
 * single focus fill used by settings rows, action buttons and sidebar rows so
 * the "two focus styles on one screen" bug cannot come back.
 */
@Composable
internal fun TvFocusSurface(focused: Boolean, radius: Dp, scale: Float, modifier: Modifier = Modifier,
    unfocused: Color = Palette.text.copy(alpha = .06f), content: @Composable () -> Unit) {
    Box(modifier
        .tvFocusOutline(focused, radius, scale)
        .background(if (focused) Palette.text else unfocused, androidx.compose.foundation.shape.RoundedCornerShape(radius))) {
        content()
    }
}
