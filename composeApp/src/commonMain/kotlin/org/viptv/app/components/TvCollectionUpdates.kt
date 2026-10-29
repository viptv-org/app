@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package org.viptv.app.components

import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusTargetModifierNode
import androidx.compose.ui.focus.Focusability
import androidx.compose.ui.focus.getFocusedRect
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlin.math.abs

private class CollectionBounds { var node: CollectionFocusNode? = null }
private data class CollectionFocusElement(val bounds: CollectionBounds) : ModifierNodeElement<CollectionFocusNode>() {
    override fun create() = CollectionFocusNode(bounds)
    override fun update(node: CollectionFocusNode) = Unit
    override fun InspectorInfo.inspectableProperties() { name = "tvCollectionUpdates" }
}
private class CollectionFocusNode(private val bounds: CollectionBounds) : DelegatingNode() {
    val focus = delegate(FocusTargetModifierNode(focusability = Focusability.Never))
    override fun onAttach() { bounds.node = this }
    override fun onDetach() { if (bounds.node === this) bounds.node = null }
}

@Composable
internal fun Modifier.tvCollectionUpdates(
    state: ScrollableState,
    horizontal: Boolean,
    revision: () -> Pair<Int, List<Pair<Int, Any>>>,
): Modifier {
    if (!LocalTvFocusAnchoring.current) return this
    val anchor = LocalBringIntoViewSpec.current as? LeadingFocusScroll ?: return this
    val bounds = remember(state) { CollectionBounds() }
    val reduceMotion = rememberUpdatedState(LocalReducedMotion.current)
    LaunchedEffect(state, anchor) {
        var previous: Pair<Int, List<Pair<Int, Any>>>? = null
        snapshotFlow { revision() to state.isScrollInProgress }
            .filter { !it.second }.map { it.first }.distinctUntilChanged()
            .collectLatest { current ->
                val old = previous
                previous = current
                if (old == null) return@collectLatest
                val oldKeys = old.second.toMap()
                // A new visible window is ordinary scrolling, not a content revision.
                if (old.first == current.first && current.second.none { (index, key) ->
                        index in oldKeys && oldKeys[index] != key
                    }) return@collectLatest
                withFrameNanos { }
                if (state.isScrollInProgress) return@collectLatest
                val node = bounds.node?.takeIf { it.isAttached } ?: return@collectLatest
                val rect = node.focus.getFocusedRect() ?: return@collectLatest
                val container = node.requireLayoutCoordinates()
                val delta = anchor.logicalScrollDistance(
                    if (horizontal) rect.left else rect.top,
                    if (horizontal) rect.width else rect.height,
                    (if (horizontal) container.size.width else container.size.height).toFloat())
                if (abs(delta) <= .5f || delta > 0 && !state.canScrollForward || delta < 0 && !state.canScrollBackward) return@collectLatest
                try { if (reduceMotion.value) state.scrollBy(delta) else state.animateScrollBy(delta) }
                catch (_: CancellationException) {
                    // A new native focus or gesture mutation wins; keep observing later data changes.
                    currentCoroutineContext().ensureActive()
                }
            }
    }
    return then(CollectionFocusElement(bounds))
}
