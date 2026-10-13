package org.viptv.app.features.library

import org.viptv.engine.wire.LibraryQuery
import org.viptv.engine.wire.LibrarySnapshot

internal data class LibraryEmptyState(val title: String, val message: String, val clearFilters: Boolean)

internal fun libraryEmptyState(snapshot: LibrarySnapshot?, query: LibraryQuery, available: Boolean): LibraryEmptyState {
    val filtered = query.type != "all" || query.show != "all" || query.status != null || query.genre != null ||
        query.rated != null || query.added != null || query.episodes_left_max != null
    return when {
        snapshot?.refresh_after_millis != null -> LibraryEmptyState("Loading matching titles…", "Results update as metadata becomes available.", false)
        !available -> LibraryEmptyState("My List is unavailable.", "This connection does not currently provide a list.", false)
        filtered -> LibraryEmptyState("No matching titles.", "Try changing or clearing the filters.", true)
        query.source == "simkl" -> LibraryEmptyState("Your watchlist is empty.", "This linked account has no titles in its watchlists.", false)
        else -> LibraryEmptyState("Your list is empty.", "Use + on a movie or series to save it here.", false)
    }
}
