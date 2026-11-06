package org.viptv.app.features.library

import org.viptv.engine.wire.LibraryQuery
import org.viptv.engine.wire.LibrarySnapshot

// copy.md §Empty states — My List, and its first read failing.
internal const val MY_LIST_EMPTY_TITLE = "Your list is empty."
internal const val MY_LIST_EMPTY_BODY = "Add titles with the + button."
internal const val MY_LIST_FILTERED_TITLE = "No matching titles."
internal const val MY_LIST_FILTERED_BODY = "Try changing or clearing the filters."
internal const val MY_LIST_ARRIVING_TITLE = "Loading matching titles…"
internal const val MY_LIST_ARRIVING_BODY = "Results update as metadata becomes available."
internal const val MY_LIST_UNAVAILABLE_TITLE = "My List is unavailable."
internal const val MY_LIST_UNAVAILABLE_BODY = "This connection does not currently provide a list."
internal const val MY_LIST_FAILED_TITLE = "Couldn’t load My List"

internal data class LibraryEmptyState(val title: String, val message: String, val clearFilters: Boolean)

internal fun libraryEmptyState(snapshot: LibrarySnapshot?, query: LibraryQuery, available: Boolean): LibraryEmptyState {
    val filtered = query.type != "all" || query.show != "all" || query.status != null || query.genre != null ||
        query.rated != null || query.added != null || query.episodes_left_max != null
    return when {
        snapshot?.refresh_after_millis != null -> LibraryEmptyState(MY_LIST_ARRIVING_TITLE, MY_LIST_ARRIVING_BODY, false)
        !available -> LibraryEmptyState(MY_LIST_UNAVAILABLE_TITLE, MY_LIST_UNAVAILABLE_BODY, false)
        filtered -> LibraryEmptyState(MY_LIST_FILTERED_TITLE, MY_LIST_FILTERED_BODY, true)
        else -> LibraryEmptyState(MY_LIST_EMPTY_TITLE, MY_LIST_EMPTY_BODY, false)
    }
}