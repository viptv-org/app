package org.viptv.app.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
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
    return { index ->
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
                val current = state.firstVisibleItemIndex * stepPx + state.firstVisibleItemScrollOffset
                state.animateScrollBy(target * stepPx - current, tween(300, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)))
            }
        }
    }
}