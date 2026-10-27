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
    val episode_title: String? = null, val season: Int? = null, val episode: Int? = null,
    val source: String? = null, val episode_card: Card? = null)
/**
 * One calendar page. [sources] lists the sources that answered; [refresh_after_millis]
 * is present only while the server is still looking up library series, and then the
 * window must be read again from the first page after that delay.
 */
@Serializable
internal data class CalendarPage(val entries: List<CalendarRelease>, val next_cursor: String? = null, val complete: Boolean,
    val sources: List<String>? = null, val refresh_after_millis: Long? = null)
/** A whole window: its rows, the sources that answered (null from older servers) and the re-read delay, if any. */
internal data class CalendarRead(val entries: List<CalendarEntry>, val sources: List<String>?, val refreshAfterMillis: Long?)
internal data class CalendarWindow(val start: String, val end: String, val days: List<CalendarDay>)
internal data class CalendarLabel(val day: String, val time: String, val aired: Boolean)
internal expect fun calendarWindow(offsetDays: Int = 0): CalendarWindow
internal expect fun calendarLabel(date: String): CalendarLabel?

internal suspend fun RemoteConnection.calendar(window: CalendarWindow, type: String = "series",
    onPage: (List<CalendarEntry>) -> Unit = {}): CalendarRead {
    val codec = Json { ignoreUnknownKeys = true }
    val entries = mutableListOf<CalendarEntry>()
    val visibleDays = window.days.map { it.id }.toSet()
    val seen = mutableSetOf<String>()
    var sources: List<String>? = null
    var refreshAfter: Long? = null
    var offset = 0
    do {
        val value = call("/api/calendar?type=${type.encodeURLParameter()}&start=${window.start}&end=${window.end}&skip=$offset&limit=${if (offset == 0) 100 else 500}")
        val page = withContext(Dispatchers.Default) { codec.decodeFromJsonElement<CalendarPage>(value) }
        page.sources?.let { answered -> sources = (sources.orEmpty() + answered).distinct() }
        page.refresh_after_millis?.takeIf { it >= 0 }?.let { delay -> refreshAfter = maxOf(refreshAfter ?: 0, delay) }
        entries += withContext(Dispatchers.Default) { page.entries.mapNotNull { release ->
            val label = calendarLabel(release.date) ?: return@mapNotNull null
            if (label.day !in visibleDays || !seen.add(release.id)) return@mapNotNull null
            val coordinates = release.episode?.let { e -> release.season?.let { "S$it E$e" } ?: "Episode $e" }
            CalendarEntry(release.id, label.day, label.time, release.card,
                subtitle = listOfNotNull(coordinates, release.episode_title).joinToString(" · ").takeIf { it.isNotBlank() },
                aired = label.aired, description = release.card.description, season = release.season?.toLong(), episode = release.episode?.toLong(), releaseDate = release.date,
                source = CalendarSource.of(release.source), episodeCard = release.libraryEpisode())
        } }
        onPage(entries.toList())
        if (page.complete) break
        val next = page.next_cursor?.toIntOrNull()
            ?.takeIf { it > offset && it <= 10_000 }
            ?: throw RemoteFailure(502, "The server returned an invalid calendar page.")
        offset = next
    } while (true)
    return CalendarRead(entries, sources, refreshAfter)
}

/** A library row's episode, kept only when it is an episode of exactly the row's series. */
private fun CalendarRelease.libraryEpisode(): Card? = episode_card?.takeIf { episode ->
    source == CalendarSource.Library.wire && episode.identity.episode != null &&
        episode.parent_identity?.id == card.identity.id && episode.parent_identity.kind == card.identity.kind
}
