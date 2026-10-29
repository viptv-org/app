package org.viptv.app.features.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot

// A LazyRow keeps the key of its first visible card, so a card inserted before it would sit off-screen to the left.
@Composable
internal fun FollowNewFirstCard(state: LazyListState, firstCardKey: Any?, keepPlace: Boolean) {
    val previous = remember(state) { arrayOfNulls<Any>(1) }
    val before = previous[0]
    SideEffect { previous[0] = firstCardKey }
    if (before == firstCardKey || keepPlace) return
    Snapshot.withoutReadObservation {
        if (state.isScrollInProgress) return@withoutReadObservation
        if (state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) state.requestScrollToItem(0)
    }
}
