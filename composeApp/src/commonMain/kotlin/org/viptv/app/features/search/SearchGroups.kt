package org.viptv.app.features.search

import org.viptv.engine.wire.Card
import org.viptv.engine.wire.IntegrationConnection

internal data class SearchGroup(val id: String, val label: String, val cards: List<Card>)
internal fun Card.searchCategory(): String = category ?: when (identity.kind) {
    "movie" -> "movie"
    "channel", "tv" -> "channel"
    else -> "series"
}
internal fun searchGroups(cards: List<Card>, addons: List<IntegrationConnection>): List<SearchGroup> {
    val providers = cards.map { it.provider_id }.distinct()
    return cards.groupBy { it.provider_id to it.searchCategory() }.map { (key, titles) ->
        val category = when (key.second) { "movie" -> "Movies"; "anime" -> "Anime"; "channel" -> "Channels"; else -> "Series" }
        val provider = addons.find { it.id == key.first }?.name
        SearchGroup("${key.first}:${key.second}", if (providers.size > 1 && provider != null) "$provider · $category" else category, titles)
    }
}
