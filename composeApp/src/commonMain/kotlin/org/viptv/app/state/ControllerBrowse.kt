package org.viptv.app.state

import org.viptv.app.components.ServerMessages
import org.viptv.app.connection.*
import kotlinx.coroutines.CancellationException

fun ApplicationController.browseFilter(id: String, value: String) {
    val prior = mutable.value.browseQuery
    val catalog = if (id == "catalog") mutable.value.browseControls?.catalogSelection(value) else null
    if (id == "catalog" && value.isNotBlank() && catalog == null) {
        updateState { next -> next.copy(error = "This catalog is no longer available. Choose another.") }
        return
    }
    val query = when (id) {
        "type" -> BrowseQuery(value)
        "catalog" -> prior.forCatalog(catalog)
        "genre" -> prior.firstPage().copy(genre = value.takeIf(String::isNotBlank), values = prior.values.filterNot { it.id == id })
        "search" -> prior.firstPage().copy(search = value.takeIf(String::isNotBlank), values = prior.values.filterNot { it.id == id })
        else -> prior.firstPage().copy(extras = if (value.isBlank()) prior.extras - id else prior.extras + (id to value),
            values = prior.values.filterNot { it.id == id })
    }
    if (query != prior) run { loadBrowse(query) }
}

fun ApplicationController.browseSelections(id: String, values: List<String>) {
    val prior = mutable.value.browseQuery
    if (prior.selections(id) == values) return
    val query = prior.withSelections(id, values)
    run { loadBrowse(query) }
}

fun ApplicationController.applyBrowse(query: BrowseQuery) {
    if (query == mutable.value.browseQuery) return
    run { loadBrowse(query.firstPage()) }
}

fun ApplicationController.retryBrowse() = run { loadBrowse(mutable.value.browseQuery) }

fun ApplicationController.browseMore() = run {
    val next = mutable.value.browseNext ?: return@run
    val query = mutable.value.browseQuery
    val skip = next.toIntOrNull()
    loadBrowse(if (skip != null) query.copy(skip = skip, cursor = null) else query.copy(skip = 0, cursor = next), append = true)
}

internal suspend fun ApplicationController.loadBrowse(query: BrowseQuery, append: Boolean = false) {
    if (!append) rememberNavigation(query)
    val profile = requireNotNull(mutable.value.selectedProfile)
    val controls = if (mutable.value.browseQuery.type == query.type && mutable.value.browseQuery.genre == query.genre) mutable.value.browseControls else null
    val scoped = controls ?: (local?.discoveryControls(profile, query.type) ?: requireNotNull(connection).discoveryControls(query.type, query.genre))
    if (scoped.discovery.availability != "Available") {
        updateState { next -> next.copy(browseQuery = query, browseControls = scoped, browseCards = emptyList(), browseNext = null,
            browseFailures = emptyList(),
            error = ServerMessages.human(scoped.discovery.message, "Configure a browsing integration for this profile.")) }
        return
    }
    val scopedQuery = if (scoped.provider_id == "simkl") {
        val timeframe = scoped.controls.find { it.id == "timeframe" }
        val priorTimeframe = query.extras["timeframe"]
        if (priorTimeframe != null && timeframe != null && timeframe.choices.none { it.value == priorTimeframe })
            query.copy(extras = query.extras - "timeframe" + listOfNotNull(timeframe.choices.firstOrNull()?.let { "timeframe" to it.value }).toMap())
        else query
    } else query
    val selected = scopedQuery.withDefaultCatalog(scoped)
    updateState { next -> next.copy(browseQuery = selected, browseControls = scoped,
        browseCards = if (append) next.browseCards else emptyList(), browseNext = if (append) next.browseNext else null,
        browseFailures = if (append) next.browseFailures else emptyList(), browsePageError = null) }
    val required = scoped.missingRequired(selected)
    if (required.isNotEmpty()) {
        updateState { next -> next.copy(error = "Choose the required filters: ${required.joinToString()}.") }
        return
    }
    val read = try {
        local?.let { it.browse(profile, selected) }
            ?: requireNotNull(connection).browse(selected, scoped)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        if (append && !(e is RemoteFailure && e.sessionExpired)) {
            updateState { it.copy(browsePageError = failureMessage(e) ?: "Could not load more.") }
            return
        }
        throw e
    }
    updateState { next -> next.copy(browseQuery = selected, browseControls = scoped,
        browseCards = ((if (append) next.browseCards else emptyList()) + read.cards).distinctBy(::cardLookupKey),
        browseNext = read.next_cursor,
        browseFailures = if (append) next.browseFailures else read.failures) }
}
