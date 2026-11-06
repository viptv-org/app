package org.viptv.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

internal const val TapTargetPixels = 44f

@OptIn(ExperimentalComposeUiApi::class)
internal fun ImageComposeScene.semanticsNodes(): List<SemanticsNode> =
    semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }

@OptIn(ExperimentalComposeUiApi::class)
internal fun ImageComposeScene.renderFrames(from: Long = 0L, count: Int = 60): Long {
    var frame = from
    repeat(count) { render(frame++ * 16_666_667L).close() }
    return frame
}

internal fun SemanticsNode.semanticsLabel(): String = (
    config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text })
    .joinToString(" ").ifBlank { config.getOrNull(SemanticsProperties.Role)?.toString() ?: "unlabelled" }

// SemanticsNode.size is the target as laid out; boundsInRoot is clipped to every ancestor, so it only says whether the node is on screen.
internal fun List<SemanticsNode>.tapTargetViolations(sceneWidth: Int, sceneHeight: Int): List<String> =
    filter { it.config.contains(SemanticsActions.OnClick) }.mapNotNull { node ->
        val bounds = node.boundsInRoot
        val visible = bounds.width > 0f && bounds.height > 0f && bounds.right > 0f && bounds.bottom > 0f &&
            bounds.left < sceneWidth && bounds.top < sceneHeight
        if (!visible || (node.size.width >= TapTargetPixels && node.size.height >= TapTargetPixels)) null
        else "${node.semanticsLabel()} is ${node.size.width}x${node.size.height} at ${bounds.left.toInt()},${bounds.top.toInt()}"
    }

internal fun List<SemanticsNode>.unnamedClickables(sceneWidth: Int, sceneHeight: Int): List<String> =
    filter { it.config.contains(SemanticsActions.OnClick) }.mapNotNull { node ->
        val bounds = node.boundsInRoot
        val visible = node.size.width > 0 && node.size.height > 0 && bounds.right > 0f && bounds.bottom > 0f &&
            bounds.left < sceneWidth && bounds.top < sceneHeight
        val named = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().isNotEmpty() ||
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().isNotEmpty() ||
            !node.config.getOrNull(SemanticsActions.OnClick)?.label.isNullOrEmpty()
        if (!visible || named) null
        else "${node.size.width}x${node.size.height} at ${bounds.left.toInt()},${bounds.top.toInt()}"
    }

private fun flatten(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::flatten)