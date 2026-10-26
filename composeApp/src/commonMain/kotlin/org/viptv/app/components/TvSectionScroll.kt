package org.viptv.app.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Aligns whole sections without letting a descendant card's bounds hide the section heading. */
internal class TvSectionScroll(private val state: LazyListState, private val scope: CoroutineScope) {
    private var motion: Job? = null
    private var target: Int? = null

    fun focus(index: Int, force: Boolean = false, after: (() -> Unit)? = null) {
        if (!force && target == index) return
        target = index
        motion?.cancel()
        motion = scope.launch {
            // The native list resolves the item against current layout and owns frame scheduling.
            if (index in 0 until state.layoutInfo.totalItemsCount) state.animateScrollToItem(index)
            after?.invoke()
        }
    }

    fun cancel() { motion?.cancel(); target = null }
}

@Composable
internal fun rememberTvSectionScroll(state: LazyListState): TvSectionScroll {
    val scope = rememberCoroutineScope()
    val controller = remember(state, scope) { TvSectionScroll(state, scope) }
    DisposableEffect(controller) { onDispose { controller.cancel() } }
    return controller
}
