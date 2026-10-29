package org.viptv.app.features.calendar

import org.viptv.engine.wire.Card

data class CalendarDay(val id: String, val weekday: String, val number: String, val label: String, val fullDate: String)

enum class CalendarSource(val wire: String) {
    Library("library"), Public("public");
    companion object {
        fun of(value: String?): CalendarSource = if (value == Library.wire) Library else Public
        fun named(value: String): CalendarSource? = entries.firstOrNull { it.wire == value }
    }
}

data class CalendarEntry(val id: String, val day: String, val time: String, val card: Card,
    val subtitle: String? = null, val status: String? = null, val aired: Boolean = false, val description: String? = null,
    val season: Long? = null, val episode: Long? = null, val releaseDate: String? = null,
    val source: CalendarSource = CalendarSource.Public, val episodeCard: Card? = null) {
    val library: Boolean get() = source == CalendarSource.Library
}

internal fun CalendarEntry.actionLabel(): String? {
    if (!aired) return null
    val numbered = season != null && season >= 0 && episode != null && episode > 0
    return when {
        library -> "Find S$season E$episode".takeIf { episodeCard != null && numbered }
        card.metadata_origin?.provider_id != "simkl" -> null
        card.identity.kind == "movie" -> "Find sources"
        card.identity.kind == "series" && numbered -> "Find S$season E$episode"
        else -> null
    }
}

enum class CalendarFilter(val label: String) { All("All"), YourShows("Your shows") }

internal fun calendarFilterOffered(entries: List<CalendarEntry>, sources: Set<CalendarSource>?): Boolean =
    sources?.containsAll(CalendarSource.entries) == true || (entries.any { it.library } && entries.any { !it.library })

internal fun List<CalendarEntry>.shown(filter: CalendarFilter, offered: Boolean): List<CalendarEntry> =
    if (filter == CalendarFilter.YourShows && offered) filter { it.library } else this

internal fun calendarOpener(open: (Card) -> Unit, openEntry: ((CalendarEntry) -> Unit)?): (CalendarEntry) -> Unit =
    { entry -> openEntry?.invoke(entry) ?: open(entry.card) }
