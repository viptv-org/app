package org.viptv.app.features.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.viptv.app.state.*

@Composable
internal fun LibraryRoute(state: ApplicationState, controller: ApplicationController, television: Boolean) {
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
    val cards = page?.cards ?: state.library
    LibraryScreen(cards, true, television, controller::open, { controller.navigate(Screen.History) },
        { controller.navigate(Screen.Home) }, page, state.libraryQuery, controller::libraryQuery,
        if (page?.next_cursor != null) controller::libraryMore else null, state.busy, state.error)
}
