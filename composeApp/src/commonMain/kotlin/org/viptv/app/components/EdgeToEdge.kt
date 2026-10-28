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
internal fun Modifier.bleedHorizontally(gutter: Dp): Modifier = bleedHorizontally(left = gutter, right = gutter)

/**
 * The same bleed per side, for a pane that only has page padding on one edge:
 * TV Search's results sit beside the keyboard column, so only their right edge
 * runs under the page gutter to the screen edge (app#111). A zero side keeps
 * that edge where it is, which also keeps the bleed off the neighbouring
 * column. The sides are physical, like the placement below.
 */
internal fun Modifier.bleedHorizontally(left: Dp, right: Dp): Modifier = layout { measurable, constraints ->
    val leftSide = left.roundToPx()
    val extra = leftSide + right.roundToPx()
    if (extra <= 0) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val wide = constraints.copy(
        minWidth = if (constraints.minWidth == 0) 0 else constraints.minWidth + extra,
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else Constraints.Infinity)
    val placeable = measurable.measure(wide)
    // `place`, not `placeRelative`: the sides are physical, so a symmetric bleed needs no
    // mirroring and a one-sided one names its own side.
    layout(constraints.constrainWidth(placeable.width - extra), placeable.height) { placeable.place(-leftSide, 0) }
}

/**
 * A `horizontalScroll` chip strip inside a page padded by [gutter]: the chips
 * scroll out to the screen edges and the first one rests on the gutter. TV
 * callers pass no gutter; their focus scrolling keeps its own insets.
 */
internal fun Modifier.edgeToEdgeScroll(state: ScrollState, gutter: Dp): Modifier =
    bleedHorizontally(gutter).horizontalScroll(state).padding(horizontal = gutter)
