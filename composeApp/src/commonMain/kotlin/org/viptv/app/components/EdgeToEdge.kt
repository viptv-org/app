package org.viptv.app.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth

/**
 * Lets content run under its parent's side padding to the screen edges
 * (app#63): it is measured [gutter] wider on each side and shifted back by
 * [gutter], so a parent that pads its content by [gutter] no longer clips a
 * scrolling strip there. Use it on a horizontally scrolling strip, or on a
 * vertical list whose rows scroll sideways; either way the content insets
 * itself by [gutter] again (`contentPadding`, or [edgeToEdgeScroll]) so the
 * first item still rests on the page gutter.
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
    // `place`, not `placeRelative`: the bleed is the same on both sides, so RTL needs no mirroring.
    layout(constraints.constrainWidth(placeable.width - extra), placeable.height) { placeable.place(-side, 0) }
}

/**
 * A `horizontalScroll` chip strip inside a page padded by [gutter]: the chips
 * scroll out to the screen edges and the first one rests on the gutter. TV
 * callers pass no gutter; their focus scrolling keeps its own insets.
 */
internal fun Modifier.edgeToEdgeScroll(state: ScrollState, gutter: Dp): Modifier =
    bleedHorizontally(gutter).horizontalScroll(state).padding(horizontal = gutter)
