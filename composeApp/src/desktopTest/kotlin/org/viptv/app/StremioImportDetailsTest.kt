package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class StremioImportDetailsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    @AfterTest fun close() = scope.cancel()

    private fun session(transport: StremioImportTransport, changed: () -> Unit = {}, limit: Long = 60_000) =
        StremioImportSession(scope, transport, "2", "Synthetic profile", false, changed = changed, detailsPoll = 5, detailsLimit = limit)
    private fun StremioImportSession.importNow() {
        connect("synthetic@example.invalid", "synthetic-password"); reviewItems(); toConfirm(); setConfirmed(true); apply()
    }
    private fun sequence(vararg statuses: StremioDetailsStatus?): suspend () -> StremioDetailsStatus? {
        var next = 0
        return { statuses[minOf(next++, statuses.lastIndex)] }
    }
    private suspend fun StremioImportSession.until(predicate: (StremioImportState) -> Boolean) =
        withTimeout(5_000) { state.first(predicate) }
    private fun FakeStremioTransport.detailsCalls() = detailsReads.get()

    @Test fun completionFollowsTheFillUntilTheServerFinishesAndThenRefreshesHome() = runBlocking<Unit> {
        val transport = FakeStremioTransport()
        val statuses = sequence(StremioDetailsStatus(true, 0, 0), StremioDetailsStatus(true, 40, 162),
            StremioDetailsStatus(true, 162, 162, 160), StremioDetailsStatus(false, 162, 162, 161))
        var readBeforeApply = false
        transport.details = { readBeforeApply = readBeforeApply || transport.calls.none { it.startsWith("apply") }; statuses() }
        var changes = 0
        val session = session(transport, changed = { changes++ })
        val seen = mutableListOf<StremioTitleDetails?>()
        val watcher = scope.launch { session.state.collect { seen += it.details } }
        session.importNow()
        assertEquals(StremioImportStage.Complete, session.state.value.stage)
        val running = session.state.value
        assertNull(running.busy, "Following the fill never makes the screen busy")
        val ready = session.until { it.details?.phase == StremioDetailsPhase.Ready }
        assertEquals(StremioTitleDetails(162, 162, StremioDetailsPhase.Ready, 161), ready.details)
        assertTrue(StremioTitleDetails(0, 0, StremioDetailsPhase.Running) in seen, "Totals start unknown: $seen")
        assertTrue(StremioTitleDetails(40, 162, StremioDetailsPhase.Running) in seen, "$seen")
        delay(60)
        assertEquals(4, transport.detailsCalls(), "Polling stops once the server reports the run finished")
        assertEquals(2, changes, "Home is checked after apply and again once the cards are filled")
        assertFalse(readBeforeApply, "Details are read only after the import is applied")
        watcher.cancel()
    }

    @Test fun leavingTheScreenOrStartingAnotherPreviewStopsPolling() = runBlocking<Unit> {
        listOf<(StremioImportSession) -> Unit>({ it.dispose() }, { it.startAnother() }).forEach { leave ->
            val transport = FakeStremioTransport()
            transport.details = { StremioDetailsStatus(true, 3, 10) }
            val session = session(transport)
            session.importNow()
            session.until { it.details?.done == 3 }
            leave(session)
            assertNull(session.state.value.details)
            delay(20)
            val calls = transport.detailsCalls()
            delay(60)
            assertEquals(calls, transport.detailsCalls(), "No polling after leaving")
        }
    }

    @Test fun anOlderServerShowsNothingAndNothingToFillShowsNothing() = runBlocking<Unit> {
        for (answer in listOf<StremioDetailsStatus?>(null, StremioDetailsStatus(false, 0, 0))) {
            val transport = FakeStremioTransport()
            transport.details = { answer }
            var changes = 0
            val session = session(transport, changed = { changes++ })
            session.importNow()
            delay(60)
            assertNull(session.state.value.details, "$answer")
            assertEquals(1, transport.detailsCalls(), "$answer")
            assertEquals(1, changes, "Only the import itself changed the library")
        }
    }

    @Test fun repeatedFailuresAndTheTimeCapLeaveTheFillToTheBackground() = runBlocking<Unit> {
        val failing = FakeStremioTransport()
        var attempt = 0
        failing.details = { if (attempt++ == 1) StremioDetailsStatus(true, 5, 50) else throw StremioTransportFailure(false) }
        val first = session(failing)
        first.importNow()
        val background = first.until { it.details?.phase == StremioDetailsPhase.Background }
        assertEquals(StremioTitleDetails(5, 50, StremioDetailsPhase.Background), background.details,
            "A single failure keeps polling; three in a row stop and keep the last counts")
        delay(30)
        assertEquals(5, failing.detailsCalls())

        val slow = FakeStremioTransport()
        slow.details = { StremioDetailsStatus(true, 1, 500) }
        val second = session(slow, limit = 40)
        second.importNow()
        assertEquals(StremioDetailsPhase.Background, second.until { it.details?.phase == StremioDetailsPhase.Background }.details?.phase)
        val calls = slow.detailsCalls()
        delay(40)
        assertEquals(calls, slow.detailsCalls(), "Polling stops at the cap")
    }

    @Test fun remoteDetailsReadsTheProfilesFillAndToleratesOlderServers() = runBlocking<Unit> {
        var answer: Pair<HttpStatusCode, String> = HttpStatusCode.OK to """{"running":true,"done":40,"total":162}"""
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            paths += "${request.method.value} ${request.url.encodedPath}"
            respond(answer.second, answer.first, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }, client, browserMode = false)
        val transport = RemoteStremioImport(remote)
        assertEquals(StremioDetailsStatus(true, 40, 162), transport.details("2"))
        assertEquals(listOf("GET /api/profiles/2/library/details"), paths)
        answer = HttpStatusCode.OK to """{"running":false,"done":163,"filled":0,"total":163}"""
        assertEquals(StremioDetailsStatus(false, 163, 163, 0), transport.details("2"), "Looked up, but no metadata add-on knew them")
        answer = HttpStatusCode.NotFound to """{"error":"API route not found"}"""
        assertNull(transport.details("2"), "An older server has no progress to show")
        for (body in listOf("""{"running":true,"done":5,"total":2}""", """{"running":"yes","done":0,"total":0}""",
            """{"running":true,"done":-1,"total":3}""", """{"running":true,"done":2,"filled":3,"total":3}""", "<html>")) {
            answer = HttpStatusCode.OK to body
            assertFailsWith<StremioInvalidResponse>(body) { transport.details("2") }
        }
        answer = HttpStatusCode.Forbidden to """{"error":"Profile access denied"}"""
        assertFailsWith<StremioImportFailure> { transport.details("2") }
        remote.close()
    }

    @Test fun theControllersImportChecksRevisionsSoHomeAndMyListPickUpTheCards() = runBlocking<Unit> {
        val base = mapOf("library" to "4", "configuration" to "2", "addons" to "a1", "policy" to "0", "preferences" to "1", "profiles" to "7")
        val moved = base + ("library" to "9")
        fun home(revision: Long) = HomeSnapshot(Scope("server", "owner", "12", revision), revision, rows = listOf(
            HomeRow(id = "library", title = "My List", provider_id = "library", state = RowState.READY, cards = listOf(
                Card(identity = MediaIdentity("tt0000001", "movie", emptyList(), null), provider_id = "stremio", title = "Imported",
                    badges = emptyList(), description = if (revision > 6) "Full description" else null)))), complete = true)
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath; paths += path
            val body = when (path) {
                "/api/profiles/12/revisions" -> JsonObject(moved.mapValues { JsonPrimitive(it.value) }).toString()
                "/api/bootstrap" -> buildJsonObject {
                    put("identity", buildJsonObject {
                        put("profiles", buildJsonArray { add(buildJsonObject { put("id", "12"); put("name", "Parent") }) })
                        put("profile_id", "12")
                    })
                    put("home", Json.encodeToJsonElement(home(7))); put("integrations", JsonArray(emptyList()))
                    put("revisions", JsonObject(moved.mapValues { JsonPrimitive(it.value) }))
                }.toString()
                else -> error("Unexpected $path")
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val controller = ApplicationController(this, store)
        controller.connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "12", home = home(6),
            profiles = listOf(Profile("12", "Parent", null)))
        controller.freshness.homeBuilt(base)
        val session = assertNotNull(controller.openStremioImport(restricted = false))
        session.changed()
        while (true) { val job = controller.quietJob ?: break; job.join(); if (controller.quietJob === job) break }
        assertEquals(listOf("/api/profiles/12/revisions", "/api/bootstrap"), paths, "One revisions check, then one quiet rebuild")
        val state = controller.state.value
        assertEquals("Full description", state.home?.rows?.single()?.cards?.single()?.description)
        assertEquals(Screen.Settings, state.screen, "The viewer stays on the import screen")
        assertFalse(state.busy)
        controller.closeStremioImport(session)
        controller.connection?.close()
    }
}
