package org.viptv.app.features.details

import org.viptv.engine.wire.Card

/** A provider can use the same title id for several numbered episodes. */
internal fun episodeKey(card: Card): String = listOf(card.provider_id, card.identity.kind,
    card.identity.id, card.identity.episode?.order, card.identity.episode?.season,
    card.identity.episode?.episode, card.identity.edition).joinToString("|") { value ->
    value?.toString()?.let { "${it.length}:$it" } ?: "-"
}

internal data class DetailSeason(val number: Long?, val episodes: List<Card>) {
    val label: String get() = when (number) { null -> "Other episodes"; 0L -> "Specials"; else -> "Season $number" }
    val countLabel: String get() = "${episodes.size} ${if (episodes.size == 1) "episode" else "episodes"}"
}

/** Preserve unknown episode metadata; never guess a season from provider display text. */
internal fun detailSeasons(episodes: List<Card>): List<DetailSeason> = episodes.distinctBy(::episodeKey)
    .groupBy { it.identity.episode?.season }
    .entries.sortedWith(compareBy({ it.key == null }, { it.key ?: 0L }))
    .map { (season, cards) -> DetailSeason(season, cards.sortedBy { it.identity.episode?.episode ?: Long.MAX_VALUE }) }
