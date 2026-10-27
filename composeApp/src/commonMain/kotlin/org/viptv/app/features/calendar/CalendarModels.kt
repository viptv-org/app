package org.viptv.app.features.calendar

import org.viptv.engine.wire.Card

data class CalendarDay(val id: String, val weekday: String, val number: String, val label: String, val fullDate: String)

/**
 * Where a calendar row comes from. [Library] rows are episodes of the profile's own
 * series; [Public] rows are the public release schedule. Older servers send no source,
 * and their rows are public.
 */
enum class CalendarSource(val wire: String) {
    Library("library"), Public("public");
    companion object {
        /** An entry's source; a missing or unknown value is public. */
        fun of(value: String?): CalendarSource = if (value == Library.wire) Library else Public
        /** A source the page says answered; unknown names are ignored. */
        fun named(value: String): CalendarSource? = entries.firstOrNull { it.wire == value }
    }
}

/** [episodeCard] is the playable episode the server sends with a [CalendarSource.Library] row. */
data class CalendarEntry(val id: String, val day: String, val time: String, val card: Card,
    val subtitle: String? = null, val status: String? = null, val aired: Boolean = false, val description: String? = null,
    val season: Long? = null, val episode: Long? = null, val releaseDate: String? = null,
    val source: CalendarSource = CalendarSource.Public, val episodeCard: Card? = null) {
    val library: Boolean get() = source == CalendarSource.Library
}

/** Library rows carry their own episode, so any aired one can be found directly; public rows need Simkl coordinates. */
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

/** The All / Your shows choice. All is the default. */
enum class CalendarFilter(val label: String) { All("All"), YourShows("Your shows") }

/**
 * The All / Your shows choice is offered only when a page can hold both kinds of rows:
 * the server answered from both sources, or (an older or partial answer) rows of both kinds arrived.
 */
internal fun calendarFilterOffered(entries: List<CalendarEntry>, sources: Set<CalendarSource>?): Boolean =
    sources?.containsAll(CalendarSource.entries) == true || (entries.any { it.library } && entries.any { !it.library })

/** The rows a filter shows. Your shows applies only while the choice is offered, so a hidden filter never hides rows. */
internal fun List<CalendarEntry>.shown(filter: CalendarFilter, offered: Boolean): List<CalendarEntry> =
    if (filter == CalendarFilter.YourShows && offered) filter { it.library } else this

/** Opening a chosen row: [openEntry] when the screen has one, else the row's title card. */
internal fun calendarOpener(open: (Card) -> Unit, openEntry: ((CalendarEntry) -> Unit)?): (CalendarEntry) -> Unit =
    { entry -> openEntry?.invoke(entry) ?: open(entry.card) }
