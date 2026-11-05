package org.viptv.app.features.details

import org.viptv.app.connection.DetailFocus
import org.viptv.engine.wire.Card
import org.viptv.app.connection.seasonNumber

internal fun DetailFocus?.seasonIn(seasons: List<DetailSeason>): DetailSeason? =
    this?.let { focus -> seasons.find { it.number == focus.season.seasonNumber() } }

internal fun DetailFocus?.episodeIn(season: DetailSeason?): Int? {
    val focus = this ?: return null
    if (season == null || focus.season.seasonNumber() != season.number) return null
    val number = focus.episode ?: return null
    return season.episodes.indexOfFirst { it.identity.episode?.episode == number }.takeIf { it >= 0 }
}

internal fun DetailFocus?.episodeIn(season: Long?, episodes: List<Card>): Int? {
    val focus = this ?: return null
    if (focus.season.seasonNumber() != season.seasonNumber()) return null
    val number = focus.episode ?: return null
    return episodes.indexOfFirst { it.identity.episode?.episode == number }.takeIf { it >= 0 }
}
