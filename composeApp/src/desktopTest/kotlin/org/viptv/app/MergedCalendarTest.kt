package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.features.calendar.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

/**
 * The merged calendar (app#46) against fake responses shaped like the documented
 * `/calendar` wire (backend PR #25, docs/SIMKL_V3.md): `source` per entry,
 * `episode_card` on library entries, page `sources`, and `refresh_after_millis`
 * only while library lookups are still running.
 */
class MergedCalendarTest {
    @get:org.junit.Rule val clock = MergedCalendarWire.fixedClock()
    @Test fun anOldServerResponseIsPublicAndOffersNoFilter() = runBlocking {
        val read = read(page(listOf(MergedCalendarWire.public("public-1", "Public show"))))
        val entry = read.entries.single()
        assertEquals(CalendarSource.Public, entry.source)
        assertFalse(entry.library)
        assertNull(entry.episodeCard)
        assertNull(read.sources)
        assertNull(read.refreshAfterMillis)
        assertFalse(calendarFilterOffered(read.entries, read.sources))
        assertEquals(read.entries, read.entries.shown(CalendarFilter.YourShows, offered = false), "A hidden filter never hides rows")
    }

    @Test fun aMergedPageKeepsSourcesLibraryEpisodesAndFiltersToYourShows() = runBlocking {
        val read = read(page(listOf(MergedCalendarWire.library("lib-1", "Your series", 2, 3),
            MergedCalendarWire.public("public-1", "Public show", source = "public")), sources = listOf("library", "public")))
        val (library, public) = read.entries
        assertEquals(CalendarSource.Library, library.source)
        assertEquals(MergedCalendarWire.episodeId("Your series", 2, 3), library.episodeCard?.identity?.id)
        assertEquals(EpisodeCoordinates("aired", 2, 3), library.episodeCard?.identity?.episode)
        assertEquals("S2 E3 · Chapter 3", library.subtitle)
        assertEquals(CalendarSource.Public, public.source)
        assertNull(public.episodeCard)
        assertEquals(setOf(CalendarSource.Library, CalendarSource.Public), read.sources)
        assertTrue(calendarFilterOffered(read.entries, read.sources))
        assertEquals(listOf("lib-1"), read.entries.shown(CalendarFilter.YourShows, offered = true).map { it.id })
        assertEquals(listOf("lib-1", "public-1"), read.entries.shown(CalendarFilter.All, offered = true).map { it.id })
    }

    @Test fun aLibraryOnlyProfileGetsItsEpisodesAndNoFilter() = runBlocking {
        val read = read(page(listOf(MergedCalendarWire.library("lib-1", "Your series", 1, 2),
            MergedCalendarWire.library("lib-2", "Your series", 1, 3)), sources = listOf("library")))
        assertTrue(read.entries.all { it.library && it.episodeCard != null })
        assertFalse(calendarFilterOffered(read.entries, read.sources))
    }

    @Test fun anEpisodeCardOfAnotherSeriesIsNotTrusted() = runBlocking {
        val stray = MergedCalendarWire.library("lib-1", "Your series", 1, 2, parent = "Another series")
        val entry = read(page(listOf(stray), sources = listOf("library"))).entries.single()
        assertTrue(entry.library)
        assertNull(entry.episodeCard)
        assertNull(entry.actionLabel(), "Without its episode a library row offers no direct Find")
    }

    @Test fun anEpisodeWhoseParentSharesAnExternalIdOfTheSeriesIsKept() = runBlocking {
        val entry = MergedCalendarWire.library("lib-1", "Your series", 1, 2).let { json ->
            // The row's series card is a Simkl title that lists the IMDb id the episode's parent uses.
            val card = buildJsonObject {
                json.getValue("card").jsonObject.forEach { (key, value) -> put(key, value) }
                put("identity", buildJsonObject { put("id", "simkl:tv:20"); put("kind", "series")
                    put("ids", buildJsonArray { add(buildJsonObject { put("namespace", "imdb"); put("value", MergedCalendarWire.seriesId("Your series").removePrefix("imdb:")) }) }) })
            }
            JsonObject(json + ("card" to card))
        }
        val read = read(page(listOf(entry), sources = listOf("library")))
        assertEquals(MergedCalendarWire.episodeId("Your series", 1, 2), read.entries.single().episodeCard?.identity?.id)
    }

    @Test fun aRefreshHintStopsPagingOnUnstableCursors() = runBlocking {
        val skips = mutableListOf<String?>()
        val remote = RemoteConnection("https://fixture.example", client = client { request ->
            skips += request.url.parameters["skip"]
            buildJsonObject {
                put("entries", JsonArray(listOf(MergedCalendarWire.library("lib-1", "Your series", 1, 2)))); put("complete", false)
                put("next_cursor", "100"); put("sources", buildJsonArray { add("library") }); put("refresh_after_millis", 15_000)
            }.toString()
        }, browserMode = false)
        try {
            val read = remote.calendar(MergedCalendarWire.window())
            assertEquals(listOf<String?>("0"), skips, "No read past the first page while the list can still grow")
            assertEquals(15_000, read.refreshAfterMillis)
            assertEquals(listOf("lib-1"), read.entries.map { it.id })
        } finally { remote.close() }
    }

    @Test fun rereadsContinueWhileTheServerKeepsAskingAndStopWhenTheCalendarCloses() = runBlocking {
        var reads = 0
        val client = client { request ->
            if (!request.url.encodedPath.startsWith("/api/calendar")) return@client null
            reads++
            page(listOf(MergedCalendarWire.library("lib-1", "Your series", 1, 2)), sources = listOf("library"), refreshAfter = 1_000)
        }
        val (controller, remote) = controller(client, this)
        try {
            controller.navigate(Screen.Calendar)
            controller.job?.join()
            withTimeout(9_000) { while (reads < 6) delay(50) }
            controller.navigate(Screen.Library)
            val seen = reads
            delay(1_500)
            assertTrue(reads <= seen + 1, "Closing the Calendar ends the re-reads: $seen then $reads")
        } finally { controller.calendarReread?.cancel(); controller.job?.cancelAndJoin(); remote.close() }
    }

    @Test fun refreshAfterRereadsTheWindowFromTheFirstPageQuietly() = runBlocking {
        val skips = mutableListOf<String?>()
        val client = client { request ->
            if (!request.url.encodedPath.startsWith("/api/calendar")) return@client null
            skips += request.url.parameters["skip"]
            when (skips.size) {
                1 -> page(listOf(MergedCalendarWire.library("lib-1", "Your series", 1, 2)), sources = listOf("library", "public"), refreshAfter = 1_000)
                else -> page(listOf(MergedCalendarWire.library("lib-1", "Your series", 1, 2), MergedCalendarWire.library("lib-2", "Late series", 4, 1),
                    MergedCalendarWire.public("public-1", "Public show", source = "public")), sources = listOf("library", "public"))
            }
        }
        val (controller, remote) = controller(client, this)
        try {
            controller.navigate(Screen.Calendar)
            controller.job?.join()
            assertEquals(listOf("lib-1"), controller.state.value.calendarEntries.map { it.id })
            assertFalse(controller.state.value.busy)
            val busy = mutableListOf<Boolean>()
            val watcher = launch { controller.state.collect { busy += it.busy } }
            withTimeout(5_000) { while (controller.state.value.calendarEntries.size < 3) delay(20) }
            assertEquals(listOf("lib-1", "lib-2", "public-1"), controller.state.value.calendarEntries.map { it.id })
            assertEquals(listOf<String?>("0", "0"), skips, "The re-read starts at the first page")
            assertFalse(busy.any { it }, "The re-read never shows a loading cover")
            delay(1_500)
            assertEquals(2, skips.size, "No refresh hint: no further reads")
            watcher.cancel()
        } finally { controller.calendarReread?.cancel(); remote.close() }
    }

    @Test fun leavingTheCalendarEndsAPendingReread() = runBlocking {
        var reads = 0
        val client = client { request ->
            if (!request.url.encodedPath.startsWith("/api/calendar")) return@client null
            reads++
            page(listOf(MergedCalendarWire.library("lib-$reads", "Your series", 1, 2)), sources = listOf("library"), refreshAfter = 1_000)
        }
        val (controller, remote) = controller(client, this)
        try {
            controller.navigate(Screen.Calendar)
            controller.job?.join()
            controller.navigate(Screen.Library)
            delay(1_500)
            assertEquals(1, reads)
            assertNotNull(controller.calendarReread).join()
        } finally { controller.calendarReread?.cancel(); controller.job?.cancelAndJoin(); remote.close() }
    }

    @Test fun choosingALibraryEntryOpensItsSeriesDetailsThroughTheEpisode() = runBlocking {
        val details = mutableListOf<String>()
        val client = client { request ->
            if (!request.url.encodedPath.startsWith("/api/meta/")) return@client null
            details += request.url.encodedPath
            Json.encodeToString(DetailSnapshot(MergedCalendarWire.seriesCard("Your series"), episodes = emptyList()))
        }
        val read = read(page(listOf(MergedCalendarWire.library("lib-1", "Your series", 2, 3),
            MergedCalendarWire.public("public-1", "Public show", source = "public")), sources = listOf("library", "public")))
        val (controller, remote) = controller(client, this)
        try {
            val library = read.entries.first()
            controller.openCalendarEntry(library)
            controller.job?.join()
            assertEquals(Screen.Details, controller.state.value.screen)
            assertEquals(listOf("/api/meta/series/${MergedCalendarWire.seriesId("Your series").encodeURLPathPart()}"), details)
            assertEquals(MergedCalendarWire.seriesId("Your series"), controller.state.value.detail?.item?.identity?.id)
            assertEquals(library.episodeCard, controller.state.value.calendarEpisode, "Details know the episode to land on")
            assertEquals(Screen.Calendar, controller.state.value.detailReturnScreen)

            controller.openCalendarEntry(read.entries.last())
            controller.job?.join()
            assertNull(controller.state.value.calendarEpisode, "A public row names no episode")
        } finally { remote.close() }
    }

    @Test fun findingALibraryEntryStartsItsEpisodeSourcesDirectly() = runBlocking {
        var requested: String? = null
        val client = client { request ->
            val path = request.url.encodedPath
            when {
                path.startsWith("/api/meta/") -> Json.encodeToString(DetailSnapshot(MergedCalendarWire.seriesCard("Your series"), episodes = emptyList()))
                path == "/api/streams" -> { requested = Json.parseToJsonElement((request.body as TextContent).text).jsonObject["id"]?.jsonPrimitive?.content; """{"id":"job"}""" }
                path == "/api/streams/job" -> """{"done":true,"events":[]}"""
                path.endsWith("/source-ranking") -> """{"streams":[]}"""
                else -> null
            }
        }
        val entry = read(page(listOf(MergedCalendarWire.library("lib-1", "Your series", 2, 3, date = MergedCalendarWire.yesterday())),
            sources = listOf("library")), days = 2).entries.single()
        assertTrue(entry.aired)
        assertEquals("Find S2 E3", entry.actionLabel())
        val (controller, remote) = controller(client, this)
        try {
            controller.watchCalendar(entry)
            withTimeout(3_000) { while (requested == null) delay(10) }
            assertEquals(MergedCalendarWire.episodeId("Your series", 2, 3), requested)
            assertEquals(entry.episodeCard, controller.state.value.sourceTitle)
            assertEquals(entry.episodeCard, controller.state.value.calendarEpisode, "Details know the episode to land on")
            withTimeout(3_000) { while (controller.state.value.sourceReturnScreen != Screen.Details || controller.state.value.detail == null) delay(10) }
            controller.backSources()
            assertEquals(Screen.Details, controller.state.value.screen, "Back from sources lands on the series' Details")
            assertEquals(MergedCalendarWire.seriesId("Your series"), controller.state.value.detail?.item?.identity?.id)
            assertEquals(entry.episodeCard, controller.state.value.calendarEpisode, "... on the episode's season")
        } finally { controller.job?.cancelAndJoin(); controller.sourceDetailJob?.cancelAndJoin(); remote.close() }
    }

    private suspend fun read(body: String, days: Int = 1): CalendarRead {
        val remote = RemoteConnection("https://fixture.example", client = client { body }, browserMode = false)
        return try { remote.calendar(MergedCalendarWire.window(days)) } finally { remote.close() }
    }

    private fun client(answer: (HttpRequestData) -> String?) = HttpClient(MockEngine { request ->
        val body = answer(request) ?: return@MockEngine respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }) { configureTransport() }

    private fun controller(client: HttpClient, scope: CoroutineScope): Pair<ApplicationController, RemoteConnection> {
        val remote = RemoteConnection("https://fixture.example", client = client, browserMode = false)
        return ApplicationController(scope).also {
            it.connection = remote
            it.mutable.value = ApplicationState(screen = Screen.Calendar, selectedProfile = "one", preferences = PreferencesSnapshot())
        } to remote
    }

    private fun page(entries: List<JsonObject>, sources: List<String>? = null, refreshAfter: Long? = null) =
        MergedCalendarWire.page(entries, sources, refreshAfter)
}

/** Calendar responses in the documented wire shape, dated inside this week's window on a fixed clock. */
internal object MergedCalendarWire {
    fun seriesId(title: String) = "imdb:tt${title.hashCode().toUInt() % 10_000_000u}"
    fun episodeId(title: String, season: Int, episode: Int) = "${seriesId(title)}:$season:$episode"
    private fun identity(id: String, episode: Pair<Int, Int>? = null) = buildJsonObject {
        put("id", id); put("kind", "series"); put("ids", buildJsonArray {})
        episode?.let { (s, e) -> put("episode", buildJsonObject { put("order", "aired"); put("season", s); put("episode", e) }) }
    }

    fun seriesCard(title: String) = Card(MediaIdentity(seriesId(title), "series", emptyList()), provider_id = "stremio", title = title,
        badges = emptyList(), metadata_origin = MetadataOrigin("stremio"))

    private fun seriesJson(title: String, poster: String?, provider: String) = buildJsonObject {
        put("identity", identity(seriesId(title))); put("provider_id", provider); put("title", title); put("badges", buildJsonArray {})
        put("description", "$title, a scheduled series."); poster?.let { put("poster", it) }
        put("metadata_origin", buildJsonObject { put("provider_id", provider) })
    }

    fun library(id: String, title: String, season: Int, episode: Int, date: String = today(), parent: String = title, poster: String? = null) = buildJsonObject {
        put("id", id); put("card", seriesJson(title, poster, "stremio")); put("date", date)
        put("episode_title", "Chapter $episode"); put("season", season); put("episode", episode); put("source", "library")
        put("episode_card", buildJsonObject {
            put("identity", identity(episodeId(title, season, episode), season to episode)); put("parent_identity", identity(seriesId(parent)))
            put("provider_id", "stremio"); put("title", "Chapter $episode"); put("badges", buildJsonArray {})
            put("metadata_origin", buildJsonObject { put("provider_id", "stremio") })
        })
    }

    /** A public Simkl entry; without [source] it is an older server's entry. */
    fun public(id: String, title: String, source: String? = null, date: String = today(), poster: String? = null) = buildJsonObject {
        put("id", id); put("card", seriesJson(title, poster, "simkl")); put("date", date)
        put("episode_title", "Pilot"); put("season", 1); put("episode", 1); source?.let { put("source", it) }
    }

    fun page(entries: List<JsonObject>, sources: List<String>?, refreshAfter: Long?) = buildJsonObject {
        put("entries", JsonArray(entries)); put("complete", true)
        sources?.let { put("sources", JsonArray(it.map(::JsonPrimitive))) }
        refreshAfter?.let { put("refresh_after_millis", it) }
    }.toString()

    /**
     * The calendar's "now" in these tests: a fixed day, at 20:40 in the default zone, when the
     * fixtures' releases are partly aired (app#106). VIPTV_CALENDAR_TEST_NOW=HH:mm runs them at
     * another time of day.
     */
    private val now = java.time.LocalDate.of(2026, 10, 14)
        .atTime(System.getenv("VIPTV_CALENDAR_TEST_NOW")?.let(java.time.LocalTime::parse) ?: java.time.LocalTime.of(20, 40))
        .atZone(java.time.ZoneId.systemDefault())

    /** Puts the calendar on the fixed [now] for each test, then the previous clock back. */
    fun fixedClock() = org.junit.rules.TestRule { base, _ -> object : org.junit.runners.model.Statement() {
        override fun evaluate() {
            val previous = calendarClock
            calendarClock = java.time.Clock.fixed(now.toInstant(), now.zone)
            try { base.evaluate() } finally { calendarClock = previous }
        }
    } }

    private fun at(daysFromToday: Long, hour: Int) = now.toLocalDate().plusDays(daysFromToday).atTime(hour, 0)
        .atZone(now.zone).toInstant().toString()
    fun today(hour: Int = 20) = at(0, hour)
    fun yesterday() = at(-1, 20)

    /** Today, or [days] days ending today. */
    fun window(days: Int = 1): CalendarWindow {
        val ids = (days - 1 downTo 0).map { requireNotNull(calendarLabel(at(-it.toLong(), 12))).day }
        return CalendarWindow(ids.first(), ids.last(), ids.mapIndexed { index, id -> CalendarDay(id, "D", "$index", if (index == ids.lastIndex) "Today" else "Day $index", id) })
    }
}
