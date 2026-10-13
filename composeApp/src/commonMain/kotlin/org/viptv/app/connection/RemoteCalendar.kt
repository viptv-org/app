package org.viptv.app.connection

import io.ktor.http.encodeURLParameter
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
internal expect fun calendarWindow(): CalendarWindow
internal expect fun calendarLabel(date: String): CalendarLabel?

internal suspend fun RemoteConnection.calendar(window: CalendarWindow, type: String = "series"): List<CalendarEntry> {
    val codec = Json { ignoreUnknownKeys = true }
    val entries = mutableListOf<CalendarEntry>()
    var offset = 0
    do {
        val page = codec.decodeFromJsonElement<CalendarPage>(call("/api/v3/calendar?type=${type.encodeURLParameter()}&start=${window.start}&end=${window.end}&skip=$offset&limit=500"))
        entries += page.entries.mapNotNull { release ->
            val label = calendarLabel(release.date) ?: return@mapNotNull null
            if (window.days.none { it.id == label.day }) return@mapNotNull null
            val coordinates = release.episode?.let { e -> release.season?.let { "S$it E$e" } ?: "Episode $e" }
            CalendarEntry(release.id, label.day, label.time, release.card,
                subtitle = listOfNotNull(coordinates, release.episode_title).joinToString(" · ").takeIf { it.isNotBlank() },
                aired = label.aired, description = release.card.description)
        }
        offset += page.entries.size
        if (page.complete || page.entries.isEmpty()) break
    } while (true)
    return entries
}
