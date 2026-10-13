package org.viptv.app.features.details

import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeSeason

/** A provider can use the same title id for several numbered episodes. */
internal fun episodeKey(card: Card): String = listOf(card.provider_id, card.identity.kind,
    card.identity.id, card.identity.episode?.order, card.identity.episode?.season,
    card.identity.episode?.episode, card.identity.edition).joinToString("|") { value ->
    value?.toString()?.let { "${it.length}:$it" } ?: "-"
}

internal data class DetailSeason(val number: Long?, val episodes: List<Card>, val total: Long? = null, val offset: Long? = null) {
    val label: String get() = when (number) { null -> "Other episodes"; 0L -> "Specials"; else -> "Season $number" }
    val countLabel: String get() = (total ?: episodes.size.toLong()).let { "$it ${if (it == 1L) "episode" else "episodes"}" }
    val nextOffset: Long? get() = if (total != null && offset != null && episodes.size < total) offset + episodes.size else null
}

/** Preserve unknown episode metadata; never guess a season from provider display text. */
internal fun detailSeasons(episodes: List<Card>, index: List<EpisodeSeason> = emptyList()): List<DetailSeason> {
    val groups = episodes.distinctBy(::episodeKey).groupBy { it.identity.episode?.season }
    return (groups.keys + index.map { it.season }).distinct().sortedWith(compareBy({ it == null }, { it ?: 0L }))
        .map { season ->
            val descriptor = index.find { it.season == season }
            DetailSeason(season, groups[season].orEmpty().sortedBy { it.identity.episode?.episode ?: Long.MAX_VALUE },
                descriptor?.episode_count, descriptor?.offset)
        }
}
