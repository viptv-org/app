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

/** A card remains stationary until it reaches the peeking-card boundary. */
@Composable
internal fun rememberTvShelfScroll(state: LazyListState, step: Dp, viewport: Dp, count: Int, gutter: Dp): (Int) -> Unit {
    val stepPx = with(LocalDensity.current) { step.toPx() }
    val visible = floor((viewport - gutter) / step).toInt().coerceAtLeast(2)
    var offset by remember(state) { mutableIntStateOf(0) }
    var motion by remember(state) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(state) { onDispose { motion?.cancel() } }
    return { index ->
        val target = when {
            index == 0 -> 0
            index > offset + visible - 2 -> index - (visible - 2)
            index < offset + 1 -> (index - 1).coerceAtLeast(0)
            else -> offset
        }.coerceIn(0, (count - visible + 1).coerceAtLeast(0))
        if (target != offset || index == 0 && state.firstVisibleItemScrollOffset != 0) {
            offset = target
            motion?.cancel()
            motion = scope.launch {
                val current = state.firstVisibleItemIndex * stepPx + state.firstVisibleItemScrollOffset
                state.animateScrollBy(target * stepPx - current, tween(300, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)))
            }
        }
    }
}
