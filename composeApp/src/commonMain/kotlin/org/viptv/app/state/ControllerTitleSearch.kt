package org.viptv.app.state

import kotlinx.coroutines.launch
import org.viptv.app.connection.TitleSearchRequest
import org.viptv.app.features.search.searchFiltersFor

internal fun ApplicationController.openTitleSearch(failure: TitleSearchRequest) {
    val page = job
    scope.launch {
        page?.join()
        if (mutable.value.selectedProfile == null) return@launch
        updateState { state -> state.copy(detail = null, detailFocus = null, detailHistory = emptyList(),
            searchTerm = failure.query, searchFilters = searchFiltersFor(failure.type),
            pendingDetailSeason = failure.season, searchFocusResults = true) }
        navigate(Screen.Search, record = false)
        search(failure.query)
    }
}
