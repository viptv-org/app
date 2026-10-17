package org.viptv.app.connection

import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.viptv.engine.wire.Card
import org.viptv.app.features.calendar.*

@Serializable
internal data class CalendarRelease(val id: String, val card: Card, val date: String,
    val episode_title: String? = null, val season: Int? = null, val episode: Int? = null)
@Serializable
internal data class CalendarPage(val entries: List<CalendarRelease>, val next_cursor: String? = null, val complete: Boolean)
internal data class CalendarWindow(val start: String, val end: String, val days: List<CalendarDay>)
internal data class CalendarLabel(val day: String, val time: String, val aired: Boolean)
internal expect fun calendarWindow(offsetDays: Int = 0): CalendarWindow
internal expect fun calendarLabel(date: String): CalendarLabel?

internal suspend fun RemoteConnection.calendar(window: CalendarWindow, type: String = "series",
    onPage: (List<CalendarEntry>) -> Unit = {}): List<CalendarEntry> {
    val codec = Json { ignoreUnknownKeys = true }
    val entries = mutableListOf<CalendarEntry>()
    val visibleDays = window.days.map { it.id }.toSet()
    val seen = mutableSetOf<String>()
    var offset = 0
    do {
        val value = call("/api/calendar?type=${type.encodeURLParameter()}&start=${window.start}&end=${window.end}&skip=$offset&limit=${if (offset == 0) 100 else 500}")
        val page = withContext(Dispatchers.Default) { codec.decodeFromJsonElement<CalendarPage>(value) }
        entries += withContext(Dispatchers.Default) { page.entries.mapNotNull { release ->
            val label = calendarLabel(release.date) ?: return@mapNotNull null
            if (label.day !in visibleDays || !seen.add(release.id)) return@mapNotNull null
            val coordinates = release.episode?.let { e -> release.season?.let { "S$it E$e" } ?: "Episode $e" }
            CalendarEntry(release.id, label.day, label.time, release.card,
                subtitle = listOfNotNull(coordinates, release.episode_title).joinToString(" · ").takeIf { it.isNotBlank() },
                aired = label.aired, description = release.card.description, season = release.season?.toLong(), episode = release.episode?.toLong(), releaseDate = release.date)
        } }
        onPage(entries.toList())
        if (page.complete) break
        val next = page.next_cursor?.toIntOrNull()
            ?.takeIf { it > offset && it <= 10_000 }
            ?: throw RemoteFailure(502, "The server returned an invalid calendar page.")
        offset = next
    } while (true)
    return entries
}
