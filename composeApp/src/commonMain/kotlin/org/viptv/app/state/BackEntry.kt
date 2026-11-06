package org.viptv.app.state

import org.viptv.app.connection.BrowseQuery
import org.viptv.engine.wire.LiveQuery
import org.viptv.app.features.calendar.CALENDAR_DEFAULT_FILTER
import org.viptv.app.features.calendar.CALENDAR_DEFAULT_TYPE
import org.viptv.app.features.calendar.CalendarFilter
import org.viptv.engine.wire.LibraryQuery

data class BackEntry(
    val screen: Screen,
    val browse: BrowseQuery? = null,
    val searchTerm: String = "",
    val searchFilters: Set<String> = emptySet(),
    val library: LibraryQuery? = null,
    val calendarType: String = CALENDAR_DEFAULT_TYPE,
    val calendarWeek: Int = 0,
    val calendarFilter: CalendarFilter = CALENDAR_DEFAULT_FILTER,
    val liveQuery: LiveQuery = LiveQuery(offset = 0),
    val liveFavoritesOnly: Boolean = false,
)

internal fun ApplicationState.captureBackEntry(screen: Screen): BackEntry = BackEntry(
    screen = screen,
    browse = browseQuery,
    searchTerm = searchTerm,
    searchFilters = searchFilters,
    library = libraryQuery,
    calendarType = calendarType,
    calendarWeek = calendarWeek,
    calendarFilter = calendarFilter,
    liveQuery = liveQuery,
    liveFavoritesOnly = liveFavoritesOnly,
)

internal fun ApplicationState.restoreBackEntry(entry: BackEntry): ApplicationState = copy(
    browseQuery = entry.browse ?: browseQuery,
    searchTerm = entry.searchTerm,
    searchFilters = entry.searchFilters,
    libraryQuery = entry.library ?: libraryQuery,
    calendarType = entry.calendarType,
    calendarWeek = entry.calendarWeek,
    calendarFilter = entry.calendarFilter,
    liveQuery = entry.liveQuery,
    liveFavoritesOnly = entry.liveFavoritesOnly,
)

internal val restoredListScreens = setOf(
    Screen.Discover, Screen.Movies, Screen.Series, Screen.Anime, Screen.Library,
    Screen.Calendar, Screen.Live, Screen.History, Screen.ContinueWatching,
)