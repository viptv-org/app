package org.viptv.app.state

import org.viptv.app.connection.*

fun ApplicationController.browseFilter(id: String, value: String) = run {
    val prior = mutable.value.browseQuery
    val query = when (id) {
        "type" -> BrowseQuery(value)
        "catalog" -> prior.copy(catalog = value.takeIf(String::isNotBlank), connection_id = mutable.value.browseControls?.catalogs?.find { it.id == value }?.connection_id, skip = 0)
        "genre" -> prior.copy(genre = value.takeIf(String::isNotBlank), skip = 0)
        "search" -> prior.copy(search = value.takeIf(String::isNotBlank), skip = 0)
        else -> prior.copy(extras = prior.extras + (id to value), skip = 0)
    }
    loadBrowse(query)
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
    val page = local?.browse(profile, query) ?: requireNotNull(connection).browse(query, scoped)
    mutable.value = mutable.value.copy(browseQuery = query, browseControls = scoped,
        browseCards = ((if (append) mutable.value.browseCards else emptyList()) + page.cards).distinctBy { it.provider_id to it.identity },
        browseNext = page.next_cursor)
}
