package org.viptv.app.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth

/**
 * Lets a horizontally scrolling strip run under its parent's side padding to
 * the screen edges (app#63). The strip is measured [gutter] wider on each side
 * and shifted back by [gutter], so a parent that pads its content by [gutter]
 * no longer clips the strip's cards there. Pair it with
 * `contentPadding = PaddingValues(horizontal = gutter)` (or padding inside a
 * `horizontalScroll`) so the first item still rests on the page gutter.
 */
internal fun Modifier.bleedHorizontally(gutter: Dp): Modifier = layout { measurable, constraints ->
    val side = gutter.roundToPx()
    if (side <= 0) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val extra = side * 2
    val wide = constraints.copy(
        minWidth = if (constraints.minWidth == 0) 0 else constraints.minWidth + extra,
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else Constraints.Infinity)
    val placeable = measurable.measure(wide)
    layout(constraints.constrainWidth(placeable.width - extra), placeable.height) { placeable.place(-side, 0) }
}
