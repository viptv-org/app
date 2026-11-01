package org.viptv.app.components

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyRow as FoundationLazyRow
import androidx.compose.foundation.lazy.LazyColumn as FoundationLazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid as FoundationLazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

private fun Modifier.pageEdgeFades(top: Boolean, bottom: Boolean): Modifier = when {
    top && bottom -> scrollEdgeFade(ScrollEdge.Top)
        .then(scrollEdgeFade(ScrollEdge.Bottom, height = BottomBarHeight + ScrollEdgeFadeHeight))
    top -> scrollEdgeFade(ScrollEdge.Top)
    bottom -> scrollEdgeFade(ScrollEdge.Bottom, height = BottomBarHeight + ScrollEdgeFadeHeight)
    else -> this
}

// Derived so scrolling recomposes the page only when an edge starts or stops fading.
@Composable
private fun Modifier.pageEdgeFades(state: ScrollableState, top: Boolean, bottom: Boolean): Modifier {
    val backward = remember(state) { derivedStateOf { state.canScrollBackward } }.value
    val forward = remember(state) { derivedStateOf { state.canScrollForward } }.value
    return pageEdgeFades(top && backward, bottom && forward)
}

// Only the page's own list draws the fades; a list nested inside one keeps its edges.
@Composable
private fun pageFadesUnderBottomBar(): Boolean = LocalBottomBarFades.current && !LocalVerticalPage.current

// Only the page's own scroller clears the bar; a scroller nested inside one keeps its edges.
@Composable
private fun PaddingValues.withPageBottomClearance(): PaddingValues {
    val clearance = if (LocalVerticalPage.current) 0.dp else LocalBottomClearance.current
    if (clearance == 0.dp) return this
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + clearance,
    )
}

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
    wheelScrollsRow: Boolean = !LocalVerticalPage.current,
    content: LazyListScope.() -> Unit,
) {
    TvFocusScroll(horizontal = true, contentPadding = contentPadding, reverseLayout = reverseLayout) {
        val anchored = modifier.tvCollectionUpdates(state, horizontal = true) {
            state.layoutInfo.let { it.totalItemsCount to it.visibleItemsInfo.map { item -> item.index to item.key } }
        }
        val pointer = if (LocalPointerHover.current && userScrollEnabled) Modifier.pointerRowScroll(state, reverseLayout, wheelScrollsRow) else Modifier
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
    topEdgeFade: Boolean = LocalBarsFadeContent.current,
    content: LazyListScope.() -> Unit,
) {
    val bottomBar = pageFadesUnderBottomBar()
    val paddedContent = contentPadding.withPageBottomClearance()
    TvFocusScroll(contentPadding = paddedContent, reverseLayout = reverseLayout, sectionControlled = sectionControlled) {
        val anchored = modifier.tvCollectionUpdates(state, horizontal = false) {
            state.layoutInfo.let { it.totalItemsCount to it.visibleItemsInfo.map { item -> item.index to item.key } }
        }.pageEdgeFades(state, topEdgeFade, bottomBar)
        CompositionLocalProvider(LocalVerticalPage provides true) {
            FoundationLazyColumn(anchored, state, paddedContent, reverseLayout, verticalArrangement,
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
    topEdgeFade: Boolean = LocalBarsFadeContent.current,
    content: LazyGridScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focus = if (LocalTvFocusAnchoring.current) remember(state, scope) { TvGridFocus(state, scope) } else null
    val bottomBar = pageFadesUnderBottomBar()
    val paddedContent = contentPadding.withPageBottomClearance()
    DisposableEffect(focus) { onDispose { focus?.cancel() } }
    TvFocusScroll(contentPadding = paddedContent, reverseLayout = reverseLayout) {
        CompositionLocalProvider(LocalTvGridFocus provides focus, LocalVerticalPage provides true) {
            val anchored = modifier.tvCollectionUpdates(state, horizontal = false) {
                state.layoutInfo.let { it.totalItemsCount to it.visibleItemsInfo.map { item -> item.index to item.key } }
            }.pageEdgeFades(state, topEdgeFade, bottomBar)
            FoundationLazyVerticalGrid(columns, anchored,
                state, paddedContent, reverseLayout, verticalArrangement, horizontalArrangement, flingBehavior, userScrollEnabled, content = content)
        }
    }
}
