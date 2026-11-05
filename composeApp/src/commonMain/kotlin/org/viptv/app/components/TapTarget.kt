package org.viptv.app.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

internal val TapTargetMinimum = 44.dp

// Inside the clickable: the target is the clickable's content, so a fixed size or height outside it still wins.
fun Modifier.minimumTapTarget(): Modifier = this then MinimumTapTargetElement

private data object MinimumTapTargetElement : ModifierNodeElement<MinimumTapTargetNode>() {
    override fun create() = MinimumTapTargetNode()
    override fun update(node: MinimumTapTargetNode) = Unit
    override fun InspectorInfo.inspectableProperties() { name = "minimumTapTarget" }
}

private class MinimumTapTargetNode : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val target = TapTargetMinimum.roundToPx()
        val placeable = measurable.measure(constraints)
        val width = maxOf(placeable.width, target).coerceAtMost(constraints.maxWidth)
        val height = maxOf(placeable.height, target).coerceAtMost(constraints.maxHeight)
        return layout(width, height) {
            placeable.place((width - placeable.width) / 2, (height - placeable.height) / 2)
        }
    }
}