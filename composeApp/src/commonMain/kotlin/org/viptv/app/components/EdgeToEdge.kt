package org.viptv.app.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth

internal fun Modifier.bleedHorizontally(gutter: Dp): Modifier = bleedHorizontally(left = gutter, right = gutter)

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
    // `place`, not `placeRelative`: the sides are physical, so a symmetric bleed needs no mirroring.
    layout(constraints.constrainWidth(placeable.width - extra), placeable.height) { placeable.place(-leftSide, 0) }
}

internal fun Modifier.edgeToEdgeScroll(state: ScrollState, gutter: Dp): Modifier =
    bleedHorizontally(gutter).horizontalScroll(state).padding(horizontal = gutter)
