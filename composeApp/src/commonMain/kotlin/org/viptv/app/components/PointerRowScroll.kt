package org.viptv.app.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs

/**
 * True inside a vertical [FocusLazyColumn] or [FocusLazyVerticalGrid]: the page owns the
 * mouse wheel there, so a poster rail in it never turns the wheel sideways.
 */
internal val LocalVerticalPage = staticCompositionLocalOf { false }

/**
 * Mouse scrolling for a horizontal [FocusLazyRow] on pointer layouts (app#59). Foundation
 * scrolls a row only with a horizontal wheel (Shift+wheel) and never with a mouse drag.
 *
 * - Click-drag with the primary button scrolls the row once it passes the touch slop,
 *   and the drag then cancels the click on the item it started on.
 * - Unless the row sits in a vertical page ([LocalVerticalPage]), a plain vertical wheel
 *   scrolls it sideways. It goes through a vertical `scrollable`, so it keeps the
 *   platform's wheel steps and smooth scrolling, and hands the wheel on at either end.
 *   Touch drags pass through it untouched, so touch scrolling stays Foundation's.
 */
@Composable
internal fun Modifier.pointerRowScroll(state: LazyListState, reverseLayout: Boolean): Modifier {
    val forwardIsLeft = (LocalLayoutDirection.current == LayoutDirection.Rtl) != reverseLayout
    val wheel = !LocalVerticalPage.current
    val sideways = remember(state) { SidewaysWheel(state) }
    return this
        .then(if (!wheel) Modifier else Modifier
            .pointerInput(sideways) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        sideways.mouse = event.changes.all { it.type == PointerType.Mouse }
                    }
                }
            }
            .scrollable(sideways, Orientation.Vertical, reverseDirection = !reverseLayout, overscrollEffect = null))
        .pointerInput(state, forwardIsLeft) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                var travelled = 0f
                var dragging = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (dragging) change.consume()
                        break
                    }
                    val dx = change.positionChange().x
                    if (!dragging) {
                        travelled += dx
                        if (abs(travelled) <= viewConfiguration.touchSlop) continue
                        dragging = true
                        state.dispatchRawDelta(if (forwardIsLeft) travelled else -travelled)
                    } else state.dispatchRawDelta(if (forwardIsLeft) dx else -dx)
                    change.consume()
                }
            }
        }
}

/**
 * The row's own [LazyListState] seen through a vertical `scrollable`: wheel deltas move
 * the row. A touch drag is never consumed and cannot scroll it, so it reaches the parent
 * through nested scrolling as before.
 */
private class SidewaysWheel(private val row: LazyListState) : ScrollableState {
    var mouse = true
    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) =
        row.scroll(scrollPriority) {
            val inner = this
            object : ScrollScope { override fun scrollBy(pixels: Float) = if (mouse) inner.scrollBy(pixels) else 0f }.block()
        }
    override fun dispatchRawDelta(delta: Float) = if (mouse) row.dispatchRawDelta(delta) else 0f
    override val isScrollInProgress get() = row.isScrollInProgress
    override val canScrollForward get() = mouse && row.canScrollForward
    override val canScrollBackward get() = mouse && row.canScrollBackward
}
