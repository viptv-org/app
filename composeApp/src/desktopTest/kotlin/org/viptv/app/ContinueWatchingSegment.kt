package org.viptv.app

import androidx.compose.runtime.Composable
import org.viptv.app.features.continuewatching.ContinueWatchingPage
import org.viptv.app.features.library.ContinueWatchingSection
import org.viptv.app.features.library.LibraryScreen
import org.viptv.app.features.library.MyListSegment
import org.viptv.engine.wire.Card

// My List on its second segment, composed the way LibraryRoute composes it.
@Composable
internal fun ContinueWatchingSegment(page: ContinueWatchingPage, play: (Card) -> Unit = {}, open: (Card) -> Unit = {},
    more: (() -> Unit)? = null, retry: () -> Unit = {}, home: () -> Unit = {}, remove: ((Card) -> Unit)? = null,
    television: Boolean = false, cards: List<Card> = emptyList(), selectSegment: (MyListSegment) -> Unit = {}) {
    LibraryScreen(cards, available = true, television = television, open = open, history = {}, home = home,
        segment = MyListSegment.Continue, selectSegment = selectSegment,
        continueWatching = ContinueWatchingSection(page, play, more, retry, remove, home))
}