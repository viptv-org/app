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
    val subtitle: String? = null, val aired: Boolean = false, val description: String? = null,
    val season: Long? = null, val episode: Long? = null, val releaseDate: String? = null,
    val source: CalendarSource = CalendarSource.Public, val episodeCard: Card? = null, val airMillis: Long? = null) {
    val library: Boolean get() = source == CalendarSource.Library
}

// copy.md §Calendar — the row's airing status.
internal const val CALENDAR_OUT_NOW = "Out now"

internal fun CalendarEntry.statusAt(nowMillis: Long): String? = when {
    airMillis == null -> if (aired) CALENDAR_OUT_NOW else null
    nowMillis >= airMillis -> CALENDAR_OUT_NOW
    else -> calendarCountdown(airMillis - nowMillis)
}

internal fun calendarCountdown(untilMillis: Long): String {
    val minutes = (untilMillis / 60_000).coerceAtLeast(1)
    val days = minutes / 1_440
    val hours = minutes % 1_440 / 60
    val rest = minutes % 60
    return when {
        days > 0 -> "in ${days}d" + if (hours > 0) " ${hours}h" else ""
        hours > 0 -> "in ${hours}h" + if (rest > 0) " ${rest}m" else ""
        else -> "in ${minutes}m"
    }
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

internal const val CALENDAR_TODAY = "Today"

internal const val CALENDAR_TYPE_ALL = "all"
internal val CalendarTypeRequests = listOf("series", "anime", "movie")
internal const val CALENDAR_DEFAULT_TYPE = CALENDAR_TYPE_ALL
internal val CALENDAR_DEFAULT_FILTER = CalendarFilter.YourShows
internal const val CALENDAR_FALLBACK = "Nothing from your list this week — showing all releases."
internal const val CALENDAR_WEEK_LIMIT = 52

internal fun List<CalendarEntry>.airedAsOf(nowMillis: Long): List<CalendarEntry> {
    var changed = false
    val rows = map { entry ->
        val aired = entry.airMillis?.let { nowMillis >= it } ?: entry.aired
        if (aired == entry.aired) entry else { changed = true; entry.copy(aired = aired) }
    }
    return if (changed) rows else this
}

internal fun calendarFilterOffered(entries: List<CalendarEntry>, sources: Set<CalendarSource>?): Boolean =
    sources?.containsAll(CalendarSource.entries) == true || (entries.any { it.library } && entries.any { !it.library })

internal fun List<CalendarEntry>.shown(filter: CalendarFilter, offered: Boolean): List<CalendarEntry> =
    if (filter == CalendarFilter.YourShows && offered) filter { it.library } else this

internal fun List<CalendarEntry>.fallsBackToAll(filter: CalendarFilter?): Boolean =
    filter == CalendarFilter.YourShows && isNotEmpty() && none { it.library }

internal fun calendarOpener(open: (Card) -> Unit, openEntry: ((CalendarEntry) -> Unit)?): (CalendarEntry) -> Unit =
    { entry -> openEntry?.invoke(entry) ?: open(entry.card) }

internal sealed interface CalendarLine {
    data class Day(val day: CalendarDay) : CalendarLine
    data class Empty(val day: CalendarDay) : CalendarLine
    data class Entry(val entry: CalendarEntry) : CalendarLine
}

internal fun List<CalendarEntry>.dayLines(days: List<CalendarDay>): List<CalendarLine> = days.flatMap { day ->
    val releases = filter { it.day == day.id }.sortedBy { it.aired }
    listOf(CalendarLine.Day(day)) + if (releases.isEmpty()) listOf(CalendarLine.Empty(day))
    else releases.map(CalendarLine::Entry)
}

internal fun CalendarLine.key(): String = when (this) {
    is CalendarLine.Day -> "day:${day.id}"
    is CalendarLine.Empty -> "empty:${day.id}"
    is CalendarLine.Entry -> entry.id
}
