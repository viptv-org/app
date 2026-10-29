package org.viptv.app.features.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import org.viptv.app.connection.cardLookupKey
import org.viptv.engine.wire.Card

// A row at its start follows the card now first; a row the viewer is in keeps the card it shows at the leading edge.
@Composable
internal fun FollowNewFirstCard(state: LazyListState, cards: List<Card>, keepPlace: Boolean) {
    val first = cards.firstOrNull()?.let(::cardLookupKey)
    val previous = remember(state) { arrayOfNulls<Any>(1) }
    val before = previous[0]
    SideEffect { previous[0] = first }
    if (before == first) return
    Snapshot.withoutReadObservation {
        if (state.isScrollInProgress) return@withoutReadObservation
        if (state.firstVisibleItemIndex != 0 || state.firstVisibleItemScrollOffset != 0) return@withoutReadObservation
        if (!keepPlace) { state.requestScrollToItem(0); return@withoutReadObservation }
        val shown = cards.indexOfFirst { cardLookupKey(it) == before }
        if (shown > 0) state.requestScrollToItem(shown)
    }
}
