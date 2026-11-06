package org.viptv.app.features.continuewatching

import androidx.compose.runtime.Composable
import org.viptv.app.components.listPageState
import org.viptv.app.features.library.LibraryRoute
import org.viptv.app.features.library.MyListSegment
import org.viptv.app.state.*

// The Home shelf's See all and the title menu open My List on its second segment.
@Composable
internal fun ContinueWatchingRoute(state: ApplicationState, controller: ApplicationController, television: Boolean) {
    LibraryRoute(state, controller, television, MyListSegment.Continue)
}

internal data class ContinueWatchingPage(val cards: List<org.viptv.engine.wire.Card>, val loaded: Boolean,
    val busy: Boolean = false, val error: String? = null, val pageError: String? = null) {
    val state get() = listPageState(cards.size, busy, error, answered = loaded)
}