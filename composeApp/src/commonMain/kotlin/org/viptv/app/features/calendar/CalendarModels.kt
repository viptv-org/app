package org.viptv.app.features.calendar

import org.viptv.engine.wire.Card

data class CalendarDay(val id: String, val weekday: String, val number: String, val label: String, val fullDate: String)
data class CalendarEntry(val id: String, val day: String, val time: String, val card: Card,
    val subtitle: String? = null, val status: String? = null, val aired: Boolean = false, val description: String? = null,
    val season: Long? = null, val episode: Long? = null, val releaseDate: String? = null)

internal fun CalendarEntry.actionLabel(): String? = when {
    !aired || card.metadata_origin?.provider_id != "simkl" -> null
    card.identity.kind == "movie" -> "Find sources"
    card.identity.kind == "series" && season != null && season >= 0 && episode != null && episode > 0 -> "Find S$season E$episode"
    else -> null
}
