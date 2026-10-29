package org.viptv.app.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.*

// An offscreen grid row must be laid out before its column can take focus.
internal class TvGridFocus(private val state: LazyGridState, private val scope: CoroutineScope) {
    internal class Item(val focus: FocusRequester, val reveal: BringIntoViewRequester) {
        var size = IntSize.Zero
    }
    private val items = mutableMapOf<Int, Item>()
    private val rowHeights = mutableMapOf<Int, Int>()
    private var pendingIndex: Int? = null
    private var pendingSource: Int? = null
    private var motion: Job? = null
    private var generation = 0

    fun register(index: Int, item: Item) { items[index] = item }
    fun unregister(index: Int, item: Item) {
        if (items[index] === item) {
            items.remove(index)
            if (pendingSource == index) cancel()
        }
    }
    fun measured(index: Int, item: Item, size: IntSize) {
        item.size = size
        val columns = state.layoutInfo.maxSpan.coerceAtLeast(1)
        var height = size.height
        for ((otherIndex, other) in items) if (otherIndex / columns == index / columns) height = maxOf(height, other.size.height)
        rowHeights[index / columns] = height
    }
    fun focusChanged(index: Int, focused: Boolean) {
        if (pendingIndex != null && (focused && index != pendingSource || !focused && index == pendingSource)) cancel()
    }
    fun cancel() { generation++; motion?.cancel(); motion = null; pendingIndex = null; pendingSource = null }

    fun move(index: Int, key: Key): Boolean {
        val delta = when (key) { Key.DirectionUp -> -1; Key.DirectionDown -> 1; else -> { cancel(); return false } }
        val info = state.layoutInfo
        val columns = info.maxSpan.coerceAtLeast(1)
        val from = if (pendingSource == index) pendingIndex ?: index else index
        val targetRow = from / columns + if (info.reverseLayout) -delta else delta
        if (targetRow < 0 || targetRow * columns >= info.totalItemsCount) return pendingIndex != null
        val target = minOf(targetRow * columns + from % columns, info.totalItemsCount - 1)
        fun request() = items[target]?.focus?.requestPlacedFocus() == true
        if (request()) { cancel(); return true }
        val source = items[index] ?: return false
        val fromRow = index / columns
        var distance = 0
        for (row in minOf(fromRow, targetRow) until maxOf(fromRow, targetRow)) {
            distance += (rowHeights[row] ?: source.size.height) + info.mainAxisItemSpacing
        }
        val logicalOffset = if (targetRow < fromRow) -distance else distance
        val offset = (if (info.reverseLayout) -logicalOffset else logicalOffset).toFloat()
        val height = (rowHeights[targetRow] ?: source.size.height).toFloat()
        val destination = Rect(0f, offset, source.size.width.toFloat(), offset + height)
        motion?.cancel()
        val revision = ++generation
        pendingIndex = target; pendingSource = index
        motion = scope.launch {
            try {
                withTimeoutOrNull(1500) {
                    coroutineScope {
                        val reveal = launch { source.reveal.bringIntoView(destination) }
                        try {
                            while (isActive) {
                                withFrameNanos { }
                                if (request()) break
                            }
                        } finally { reveal.cancel() }
                    }
                }
            } finally {
                if (generation == revision) { pendingIndex = null; pendingSource = null }
            }
        }
        return true
    }
}

internal val LocalTvGridFocus = compositionLocalOf<TvGridFocus?> { null }

@Composable
internal fun Modifier.tvGridItem(index: Int): Modifier {
    val grid = LocalTvGridFocus.current ?: return this
    val item = remember { TvGridFocus.Item(FocusRequester(), BringIntoViewRequester()) }
    DisposableEffect(grid, index, item) {
        grid.register(index, item)
        onDispose { grid.unregister(index, item) }
    }
    return focusRequester(item.focus).bringIntoViewRequester(item.reveal)
        .onSizeChanged { grid.measured(index, item, it) }
        .onFocusChanged { grid.focusChanged(index, it.isFocused) }
        .onPreviewKeyEvent { it.type == KeyEventType.KeyDown && grid.move(index, it.key) }
}
