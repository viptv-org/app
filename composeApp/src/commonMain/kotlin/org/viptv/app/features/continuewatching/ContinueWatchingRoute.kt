package org.viptv.app.features.continuewatching

import androidx.compose.runtime.Composable
import org.viptv.app.components.listPageState
import org.viptv.app.state.*

@Composable
internal fun ContinueWatchingRoute(state: ApplicationState, controller: ApplicationController, television: Boolean) {
    val list = state.continueWatching?.takeIf { it.profile == state.selectedProfile }
    val more = if (list?.next != null) controller::continueWatchingMore else null
    val page = ContinueWatchingPage(list?.cards.orEmpty(), loaded = list != null, busy = state.busy, error = state.continueWatchingError,
        pageError = state.continueWatchingPageError)
    val remove = if (state.localMode) null else controller::removeFromContinueWatching
    if (television) TvContinueWatchingScreen(page, controller::playTitle, controller::open, more, retry = { controller.loadContinueWatching() },
        home = { controller.navigate(Screen.Home) }, remove = remove)
    else ContinueWatchingScreen(page, controller::playTitle, controller::open, more, retry = { controller.loadContinueWatching() },
        back = controller::navigateBack, home = { controller.navigate(Screen.Home) }, remove = remove)
}

internal data class ContinueWatchingPage(val cards: List<org.viptv.engine.wire.Card>, val loaded: Boolean,
    val busy: Boolean = false, val error: String? = null, val pageError: String? = null) {
    val state get() = listPageState(cards.size, busy, error, answered = loaded)
}
