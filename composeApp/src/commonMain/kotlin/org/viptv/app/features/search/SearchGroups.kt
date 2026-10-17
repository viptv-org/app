package org.viptv.app.features.search

import org.viptv.engine.wire.Card
import org.viptv.engine.wire.IntegrationConnection
import org.viptv.app.connection.SearchResultRow

internal data class SearchGroup(val id: String, val label: String, val cards: List<Card>, val message: String? = null)
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

internal fun searchResultGroups(rows: List<SearchResultRow>, filter: String = "All"): List<SearchGroup> = rows.map { row ->
    val cards = row.cards.filter { it.matchesSearchFilter(filter) }.distinctBy(::searchCardKey)
    val message = when (row.state) {
        "Failed" -> "This search provider could not respond. Try again."
        "Loading", "Pending" -> "Searching…"
        "Ready" -> if (row.message != null) "Some search results are unavailable. Try again." else null
        else -> null
    }
    SearchGroup(row.id, row.title, cards, message)
}.let { groups ->
    if (groups.any { it.cards.isNotEmpty() }) groups.filter { it.cards.isNotEmpty() || it.message != null } else groups
}

internal fun Card.matchesSearchFilter(filter: String): Boolean = when (filter) {
    "Movies" -> searchCategory() == "movie"
    "Series" -> searchCategory() == "series"
    "Anime" -> searchCategory() == "anime"
    "Channels" -> identity.kind in listOf("channel", "tv")
    else -> true
}

internal fun searchCardKey(card: Card): String = org.viptv.app.connection.cardLookupKey(card)

internal fun searchFocusKey(group: SearchGroup, card: Card): String = "${group.id.length}:${group.id}|${searchCardKey(card)}"
