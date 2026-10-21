package org.viptv.app.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * Scrolls a shelf only when the focused card would leave the safe window, and
 * always from the *real* scroll position.
 *
 * Deriving the target from [LazyListState.layoutInfo] instead of a shadow copy of
 * the index matters: after a long run to the right and back, a shadow counter can
 * drift away from what is on screen, and then the correction never fires and focus
 * walks off the left edge where it cannot be seen or reached.
 */
@Composable
internal fun rememberTvShelfScroll(state: LazyListState, step: Dp, viewport: Dp, count: Int, gutter: Dp): (Int) -> Unit {
    val stepPx = with(LocalDensity.current) { step.toPx() }
    val visible = floor((viewport - gutter) / step).toInt().coerceAtLeast(2)
    val maxOffset = (count - visible + 1).coerceAtLeast(0)
    var motion by remember(state) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(state) { onDispose { motion?.cancel() } }
    // One remembered lambda: a fresh closure per composition used to cascade
    // into every card's focus callback and restart the whole shelf on a move.
    return remember(state, scope, stepPx, visible, maxOffset, count) { { index: Int ->
        val items = state.layoutInfo.visibleItemsInfo
        val first = items.firstOrNull()?.index
        val last = items.lastOrNull()?.index
        // Keep one card peeking on the leading edge and two on the trailing one.
        val target = when {
            first == null || last == null -> null
            index <= first -> (index - 1).coerceAtLeast(0)
            index >= last -> index - (visible - 2)
            else -> null
        }?.coerceIn(0, maxOffset)
        if (target != null && target != state.firstVisibleItemIndex) {
            motion?.cancel()
            motion = scope.launch {
                // Scroll *to the item* rather than by index*step pixels: the shelf has
                // a 96 px leading inset and mixed card widths, so pixel math drifted
                // and the first card could end up outside the viewport while focused.
                // Long jumps are instant: a repeating D-pad press arrives faster than a
                // 300 ms animation, so animating every press restarted the scroll each
                // time and churned composition instead of settling.
                val item = target.coerceIn(0, (count - 1).coerceAtLeast(0))
                if (kotlin.math.abs(item - state.firstVisibleItemIndex) > 2) state.scrollToItem(item)
                else state.animateScrollToItem(item, scrollOffset = 0)
            }
        }
    } }
}