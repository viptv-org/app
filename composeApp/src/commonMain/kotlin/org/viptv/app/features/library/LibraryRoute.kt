package org.viptv.app.features.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.viptv.app.features.continuewatching.ContinueWatchingPage
import org.viptv.app.state.*

@Composable
internal fun LibraryRoute(state: ApplicationState, controller: ApplicationController, television: Boolean,
    initialSegment: MyListSegment = MyListSegment.Saved) {
    var segment by rememberSaveable(state.selectedProfile) { mutableStateOf(initialSegment) }
    val page = state.libraryPage
    LaunchedEffect(state.selectedProfile, state.libraryQuery, page?.refresh_after_millis != null) {
        repeat(6) { attempt ->
            val wait = controller.state.value.libraryPage?.refresh_after_millis ?: return@LaunchedEffect
            delay(maxOf(wait, (4000L shl attempt).coerceAtMost(30_000L)).coerceIn(2_000L, 30_000L))
            if (!controller.state.value.busy) {
                controller.refreshLibraryPage()
                controller.state.first { !it.busy }
            }
        }
    }
    // The second segment reads its own list the first time the viewer opens it.
    LaunchedEffect(segment) {
        if (segment == MyListSegment.Continue) controller.ensureContinueWatching() else controller.ensureLibraryPage()
    }
    val list = state.continueWatching?.takeIf { it.profile == state.selectedProfile }
    // The segment reads its own load failure; an action's error never becomes its page state.
    val continuePage = ContinueWatchingPage(list?.cards.orEmpty(), loaded = list != null, busy = state.busy,
        error = state.continueWatchingError, pageError = state.continueWatchingPageError)
    LibraryScreen(page?.cards ?: state.library, true, television, controller::open, { controller.navigate(Screen.History) },
        { controller.navigate(Screen.Home) }, page, state.libraryQuery, controller::libraryQuery,
        if (page?.next_cursor != null) controller::libraryMore else null, state.busy, state.error,
        remove = controller::removeFromMyList, retry = controller::retryLibrary, pageError = state.libraryPageError,
        segment = segment, selectSegment = { segment = it },
        continueWatching = ContinueWatchingSection(continuePage, controller::playTitle,
            if (list?.next != null) controller::continueWatchingMore else null, { controller.loadContinueWatching() },
            if (state.localMode) null else controller::removeFromContinueWatching, { controller.navigate(Screen.Home) }))
}