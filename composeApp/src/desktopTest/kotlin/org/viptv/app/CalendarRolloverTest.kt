package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import org.viptv.app.state.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.*

class CalendarRolloverTest {
    private class MovableClock(var at: ZonedDateTime) : Clock() {
        override fun getZone(): ZoneId = at.zone
        override fun withZone(zone: ZoneId): Clock = MovableClock(at.withZoneSameInstant(zone))
        override fun instant(): Instant = at.toInstant()
    }

    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    @Test fun aDayRolloverRefetchesTheWindowAroundTheNewDay() = runBlocking<Unit> {
        val zone = ZoneId.systemDefault()
        val clock = MovableClock(ZonedDateTime.of(LocalDate.of(2026, 10, 14), LocalTime.of(23, 58), zone))
        val previous = appDateClock
        appDateClock = clock
        val windows = mutableListOf<Pair<String?, String?>>()
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            if (path == "/api/calendar") windows += request.url.parameters["start"] to request.url.parameters["end"]
            respond("""{"entries":[],"complete":true}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://fixture.example", client = client, browserMode = false)
        val controller = ApplicationController(this, store).also {
            it.connection = remote
            it.mutable.value = ApplicationState(screen = Screen.Calendar, selectedProfile = "one", preferences = PreferencesSnapshot())
        }
        try {
            controller.rolloverCalendar(); requireNotNull(controller.job).join()
            assertEquals(listOf<Pair<String?, String?>>("2026-10-10" to "2026-10-18"), windows.toList())
            assertEquals(listOf("2026-10-11", "2026-10-17"), controller.state.value.calendarDays.let { days -> listOf(days.first().id, days.last().id) })
            assertEquals("2026-10-14", controller.state.value.calendarDays.single { it.label == "Today" }.id)

            clock.at = clock.at.plusMinutes(7)
            controller.rolloverCalendar(); requireNotNull(controller.job).join()
            assertEquals("2026-10-11" to "2026-10-19", windows.last())
            assertEquals(listOf("2026-10-12", "2026-10-18"), controller.state.value.calendarDays.let { days -> listOf(days.first().id, days.last().id) })
            assertEquals("2026-10-15", controller.state.value.calendarDays.single { it.label == "Today" }.id)
        } finally {
            controller.disconnect()
            appDateClock = previous
        }
    }

    @Test fun aDayRolloverOnAnotherScreenReloadsNothing() = runBlocking<Unit> {
        val zone = ZoneId.systemDefault()
        val clock = MovableClock(ZonedDateTime.of(LocalDate.of(2026, 10, 14), LocalTime.of(23, 58), zone))
        val previous = appDateClock
        appDateClock = clock
        var requests = 0
        val client = HttpClient(MockEngine { requests++; respond("""{"entries":[],"complete":true}""",
            headers = headersOf(HttpHeaders.ContentType, "application/json")) }) { configureTransport() }
        val remote = RemoteConnection("https://fixture.example", client = client, browserMode = false)
        val controller = ApplicationController(this, store).also {
            it.connection = remote
            it.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "one", preferences = PreferencesSnapshot())
        }
        try {
            clock.at = clock.at.plusMinutes(7)
            controller.rolloverCalendar()
            assertEquals(0, requests, "the calendar only reloads while it is open")
        } finally {
            controller.disconnect()
            appDateClock = previous
        }
    }
}