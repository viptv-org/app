package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.features.calendar.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class RemoteCalendarTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val card = Card(MediaIdentity("simkl:tv:20", "series", emptyList()), provider_id = "simkl", title = "Series", badges = emptyList())
    private val date = "2026-10-15T16:00:00Z"
    private val day = requireNotNull(calendarLabel(date)).day
    private val window = CalendarWindow(day, day, listOf(CalendarDay(day, "Thu", "15", "Today", "October 15")))

    @Test fun publishesFirstPageBeforeFetchingTheRemainingPagesAndUsesServerCursor() = runBlocking {
        val second = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val snapshots = mutableListOf<List<CalendarEntry>>()
        val client = HttpClient(MockEngine { request ->
            val page = when (request.url.parameters["skip"]) {
                "0" -> {
                    assertEquals("100", request.url.parameters["limit"])
                    CalendarPage(listOf(CalendarRelease("first", card, date)), "17", false)
                }
                "17" -> {
                    assertEquals(1, snapshots.size)
                    assertEquals("500", request.url.parameters["limit"])
                    second.complete(Unit); release.await()
                    CalendarPage(listOf(CalendarRelease("first", card, date), CalendarRelease("second", card, date)), complete = true)
                }
                else -> error("Invalid cursor")
            }
            respond(Json.encodeToString(page), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            val pending = async { remote.calendar(window) { snapshots += it } }
            withTimeout(2_000) { second.await() }
            assertEquals(listOf("first"), snapshots.single().map { it.id })
            release.complete(Unit)
            assertEquals(listOf("first", "second"), pending.await().entries.map { it.id })
        } finally { remote.close() }
    }

    @Test fun refusesRepeatedOrMissingCursorsInsteadOfLooping() = runBlocking {
        for (cursor in listOf(null, "0", "-1", "10001", "bad")) {
            var requests = 0
            val client = HttpClient(MockEngine {
                requests++
                respond(Json.encodeToString(CalendarPage(listOf(CalendarRelease("first", card, date)), cursor, false)),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureTransport() }
            val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
            try {
                assertEquals(502, assertFailsWith<RemoteFailure> { remote.calendar(window) }.status)
                assertEquals(1, requests)
            } finally { remote.close() }
        }
    }

    @Test fun leavingCalendarCancelsItsPendingResponse() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine {
            started.complete(Unit); awaitCancellation()
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "owner")
        try {
            controller.navigate(Screen.Calendar)
            withTimeout(2_000) { started.await() }
            val pending = requireNotNull(controller.job)
            controller.navigate(Screen.Home)
            withTimeout(2_000) { pending.join() }
            assertEquals(Screen.Home, controller.state.value.screen)
            assertFalse(controller.state.value.busy)
            assertTrue(controller.state.value.calendarEntries.isEmpty())
        } finally { remote.close() }
    }
}
