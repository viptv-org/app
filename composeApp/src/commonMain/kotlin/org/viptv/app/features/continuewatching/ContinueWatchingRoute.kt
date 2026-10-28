package org.viptv.app.features.continuewatching

import androidx.compose.runtime.Composable
import org.viptv.app.state.*

/** The full Continue Watching list (app#114): every title in progress, opened from the end of Home's row. */
@Composable
internal fun ContinueWatchingRoute(state: ApplicationState, controller: ApplicationController, television: Boolean) {
    val list = state.continueWatching?.takeIf { it.profile == state.selectedProfile }
    val more = if (list?.next != null) controller::continueWatchingMore else null
    val page = ContinueWatchingPage(list?.cards.orEmpty(), loaded = list != null, busy = state.busy, error = state.error)
    // A card resumes exactly as on Home: straight to that episode's (or movie's) sources (app#11, app#40).
    // A caught-up card has nothing left to play and opens the show's Details instead (app#132).
    if (television) TvContinueWatchingScreen(page, controller::playTitle, controller::open, more, retry = { controller.loadContinueWatching() },
        home = { controller.navigate(Screen.Home) })
    else ContinueWatchingScreen(page, controller::playTitle, controller::open, more, retry = { controller.loadContinueWatching() },
        back = controller::navigateBack, home = { controller.navigate(Screen.Home) })
}

/** What the page shows: the loaded cards, and whether the first page has arrived at all. */
internal data class ContinueWatchingPage(val cards: List<org.viptv.engine.wire.Card>, val loaded: Boolean,
    val busy: Boolean = false, val error: String? = null) {
    /** The first page is still on its way: skeletons, not the empty state. */
    val loading get() = !loaded && error == null
    val empty get() = loaded && cards.isEmpty() && error == null
}
