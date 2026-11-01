package org.viptv.app.features.details

import org.viptv.app.connection.DetailFocus
import org.viptv.engine.wire.Card

internal fun DetailFocus?.seasonIn(seasons: List<DetailSeason>): DetailSeason? =
    this?.season?.let { number -> seasons.find { it.number == number } }

internal fun DetailFocus?.episodeIn(season: DetailSeason?): Int? {
    val focus = this ?: return null
    if (season == null || focus.season != season.number) return null
    val number = focus.episode ?: return null
    return season.episodes.indexOfFirst { it.identity.episode?.episode == number }.takeIf { it >= 0 }
}

internal fun DetailFocus?.episodeIn(season: Long?, episodes: List<Card>): Int? {
    val focus = this ?: return null
    if (focus.season != season) return null
    val number = focus.episode ?: return null
    return episodes.indexOfFirst { it.identity.episode?.episode == number }.takeIf { it >= 0 }
}
