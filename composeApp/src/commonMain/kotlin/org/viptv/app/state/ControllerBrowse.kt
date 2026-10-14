package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.browseFilter(id: String, value: String) {
    val prior = mutable.value.browseQuery
    val catalog = if (id == "catalog") mutable.value.browseControls?.catalogSelection(value) else null
    if (id == "catalog" && value.isNotBlank() && catalog == null) {
        mutable.value = mutable.value.copy(error = "This catalog is no longer available. Choose another.")
        return
    }
    val query = when (id) {
        "type" -> BrowseQuery(value)
        "catalog" -> prior.forCatalog(catalog)
        "genre" -> prior.copy(genre = value.takeIf(String::isNotBlank), values = prior.values.filterNot { it.id == id }, skip = 0)
        "search" -> prior.copy(search = value.takeIf(String::isNotBlank), values = prior.values.filterNot { it.id == id }, skip = 0)
        else -> prior.copy(extras = if (value.isBlank()) prior.extras - id else prior.extras + (id to value),
            values = prior.values.filterNot { it.id == id }, skip = 0)
    }
    if (query != prior) run { loadBrowse(query) }
}

fun ApplicationController.browseSelections(id: String, values: List<String>) {
    val prior = mutable.value.browseQuery
    if (prior.selections(id) == values) return
    val query = prior.withSelections(id, values)
    run { loadBrowse(query) }
}

fun ApplicationController.browseMore() = run {
    val skip = mutable.value.browseNext?.toIntOrNull() ?: return@run
    loadBrowse(mutable.value.browseQuery.copy(skip = skip), append = true)
}

internal suspend fun ApplicationController.loadBrowse(query: BrowseQuery, append: Boolean = false) {
    val profile = requireNotNull(mutable.value.selectedProfile)
    val controls = if (mutable.value.browseQuery.type == query.type && mutable.value.browseQuery.genre == query.genre) mutable.value.browseControls else null
    val scoped = controls ?: (local?.discoveryControls(profile, query.type) ?: requireNotNull(connection).discoveryControls(query.type, query.genre))
    if (scoped.discovery.availability != "Available") {
        mutable.value = mutable.value.copy(browseQuery = query, browseControls = scoped, browseCards = emptyList(), browseNext = null, error = scoped.discovery.message ?: "Configure a browsing integration for this profile.")
        return
    }
    val selected = query.withDefaultCatalog(scoped)
    mutable.value = mutable.value.copy(browseQuery = selected, browseControls = scoped,
        browseCards = if (append) mutable.value.browseCards else emptyList(), browseNext = if (append) mutable.value.browseNext else null)
    val required = scoped.missingRequired(selected)
    if (required.isNotEmpty()) {
        mutable.value = mutable.value.copy(error = "Choose the required filters: ${required.joinToString()}.")
        return
    }
    val page = local?.browse(profile, selected) ?: requireNotNull(connection).browse(selected, scoped)
    mutable.value = mutable.value.copy(browseQuery = selected, browseControls = scoped,
        browseCards = ((if (append) mutable.value.browseCards else emptyList()) + page.cards).distinctBy { it.provider_id to it.identity },
        browseNext = page.next_cursor)
}
