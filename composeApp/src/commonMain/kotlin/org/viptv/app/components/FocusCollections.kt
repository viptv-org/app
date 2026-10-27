package org.viptv.app.components

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyRow as FoundationLazyRow
import androidx.compose.foundation.lazy.LazyColumn as FoundationLazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid as FoundationLazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Native collections with one TV-only positioning policy. Non-TV layouts inherit Foundation defaults,
 * plus mouse wheel and drag scrolling for rows on pointer layouts ([pointerRowScroll]).
 */
@Composable
internal fun FocusLazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = if (reverseLayout) Arrangement.End else Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    TvFocusScroll(horizontal = true, contentPadding = contentPadding, reverseLayout = reverseLayout) {
        val anchored = modifier.tvCollectionUpdates(state, horizontal = true) {
            state.layoutInfo.let { it.totalItemsCount to it.visibleItemsInfo.map { item -> item.index to item.key } }
        }
        // Desktop and web: mouse wheel and click-drag scrolling (app#59). TV and touch keep Foundation's.
        val pointer = if (LocalPointerHover.current && userScrollEnabled) Modifier.pointerRowScroll(state, reverseLayout) else Modifier
        FoundationLazyRow(anchored.then(pointer), state, contentPadding, reverseLayout, horizontalArrangement,
            verticalAlignment, flingBehavior, userScrollEnabled, content = content)
    }
}

@Composable
internal fun FocusLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (reverseLayout) Arrangement.Bottom else Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    sectionControlled: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    TvFocusScroll(contentPadding = contentPadding, reverseLayout = reverseLayout, sectionControlled = sectionControlled) {
        val anchored = modifier.tvCollectionUpdates(state, horizontal = false) {
            state.layoutInfo.let { it.totalItemsCount to it.visibleItemsInfo.map { item -> item.index to item.key } }
        }
        CompositionLocalProvider(LocalVerticalPage provides true) {
            FoundationLazyColumn(anchored, state, contentPadding, reverseLayout, verticalArrangement,
                horizontalAlignment, flingBehavior, userScrollEnabled, content = content)
        }
    }
}

@Composable
internal fun FocusLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (reverseLayout) Arrangement.Bottom else Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyGridScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focus = if (LocalTvFocusAnchoring.current) remember(state, scope) { TvGridFocus(state, scope) } else null
    DisposableEffect(focus) { onDispose { focus?.cancel() } }
    TvFocusScroll(contentPadding = contentPadding, reverseLayout = reverseLayout) {
        CompositionLocalProvider(LocalTvGridFocus provides focus, LocalVerticalPage provides true) {
            val anchored = modifier.tvCollectionUpdates(state, horizontal = false) {
                state.layoutInfo.let { it.totalItemsCount to it.visibleItemsInfo.map { item -> item.index to item.key } }
            }
            FoundationLazyVerticalGrid(columns, anchored,
                state, contentPadding, reverseLayout, verticalArrangement, horizontalArrangement, flingBehavior, userScrollEnabled, content = content)
        }
    }
}
