package org.viptv.app.features.search

import org.viptv.engine.wire.Card
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.app.connection.SearchResultRow

internal data class SearchGroup(val id: String, val label: String, val cards: List<Card>, val message: String? = null, val failed: Boolean = false)
internal val searchFilterOptions = listOf("All", "Movies", "Series", "Anime", "Channels")

private const val providerFailed = "This search provider could not respond. Try again."
private const val noMatchingTitles = "No matching titles"
private const val needsCredentials = "This add-on needs valid credentials to search."

private fun SearchResultRow.errored(): Boolean = state == "Failed" && when (error_code) {
    "search_failed" -> true
    "credentials_required" -> false
    else -> message != needsCredentials
}

internal fun searchFiltersFor(type: String?): Set<String> = when (type) {
    "series" -> setOf("Series", "Anime")
    "movie" -> setOf("Movies")
    else -> emptySet()
}

internal fun Card.searchCategory(): String = category ?: when (identity.kind) {
    "movie" -> "movie"
    "channel", "tv" -> "channel"
    else -> "series"
}
internal fun searchGroups(cards: List<Card>, addons: List<IntegrationConnection>): List<SearchGroup> {
    return cards.groupBy { card -> card.provider_id to card.searchCategory().takeIf { card.provider_id == "simkl" } }.map { (key, titles) ->
        val category = when (key.second) { "movie" -> "Movies"; "anime" -> "Anime"; "channel" -> "Channels"; else -> "Series" }
        val provider = addons.find { it.id == key.first }?.name
        SearchGroup("${key.first}:${key.second}", if (key.first == "simkl") category else provider ?: "Search results", titles.distinctBy(::searchCardKey))
    }
}

internal fun searchResultGroups(rows: List<SearchResultRow>, filters: Set<String> = emptySet()): List<SearchGroup> = rows.map { row ->
    val cards = row.cards.filter { it.matchesSearchFilter(filters) }.distinctBy(::searchCardKey)
    val failed = row.errored() || (row.state == "Ready" && row.message != null)
    val message = when {
        row.errored() -> providerFailed
        row.state == "Loading" || row.state == "Pending" -> "Searching…"
        failed -> "Some search results are unavailable. Try again."
        else -> null
    }
    SearchGroup(row.id, row.title, cards, message, failed)
}.filter { it.cards.isNotEmpty() || it.message != null }.let { groups ->
    val (returned, silent) = groups.partition { it.cards.isNotEmpty() }
    if (returned.isEmpty()) emptyList() else returned + silent
}

internal fun searchFailed(rows: List<SearchResultRow>): Boolean =
    rows.any { it.errored() } && rows.none { it.cards.isNotEmpty() }

internal fun searchNoResultsCopy(rows: List<SearchResultRow>): String =
    if (searchFailed(rows)) providerFailed else noMatchingTitles

internal fun Card.matchesSearchFilter(filters: Set<String>): Boolean = filters.isEmpty() || filters.any { filter ->
    when (filter) {
        "Movies" -> searchCategory() == "movie"
        "Series" -> searchCategory() == "series"
        "Anime" -> searchCategory() == "anime"
        "Channels" -> identity.kind in listOf("channel", "tv")
        else -> true
    }
}

internal fun searchCardKey(card: Card): String = org.viptv.app.connection.cardLookupKey(card)

internal fun searchFocusKey(group: SearchGroup, card: Card): String = "${group.id.length}:${group.id}|${searchCardKey(card)}"
