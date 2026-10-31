package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class HistoryCorrectionControllerTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun card(id: String, kind: String = "movie") =
        Card(identity = MediaIdentity(id, kind, emptyList()), provider_id = "simkl", title = id, badges = emptyList())

    private fun display(id: String, position: Double = 30.0, duration: Double = 100.0, watched: Boolean = false) =
        HistoryDisplay(HistoryEntry(card(id), 1L, if (watched) 1L else null), "Today",
            position = position, duration = duration, watched = watched, resumeActive = position > 0.0 && !watched)

    private fun correctResponse(position: Double, duration: Double = 100.0, watched: Boolean = false,
        resumeActive: Boolean = false, completionOnly: Boolean = false, watchDateKnown: Boolean = true) = buildJsonObject {
        put("ok", true)
        put("position", position)
        put("duration", duration)
        put("watched", watched)
        put("resume_active", resumeActive)
        put("completion_only", completionOnly)
        put("watch_date_known", watchDateKnown)
    }.toString()

    private fun CoroutineScope.controller(handle: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): ApplicationController {
        val client = HttpClient(MockEngine { request -> handle(request) }) { configureTransport() }
        return ApplicationController(this, store).also {
            it.connection = RemoteConnection("https://fixture.example", client = client, browserMode = false)
            it.mutable.value = ApplicationState(screen = Screen.History, selectedProfile = "4")
        }
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private suspend fun ApplicationController.settleRun() {
        while (true) { val running = job ?: return; running.join(); if (job === running) return }
    }

    @Test fun markWatchedSendsActionWatchedOnceAndFlipsTheRow() = runBlocking<Unit> {
        val started = display("movie-1")
        val bodies = mutableListOf<JsonObject>()
        val controller = controller { request ->
            when (request.url.encodedPath) {
                "/api/profiles/4/progress/correct" -> {
                    bodies += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    json(correctResponse(100.0, watched = true))
                }
                else -> error("Unexpected ${request.url}")
            }
        }
        controller.mutable.value = controller.state.value.copy(history = listOf(started))
        try {
            controller.correctHistory(started, HistoryCorrection.Watched)
            controller.settleRun()
            assertEquals(1, bodies.size)
            assertEquals("watched", bodies[0]["action"]?.jsonPrimitive?.content)
            assertEquals("movie-1", bodies[0]["id"]?.jsonPrimitive?.content)
            assertEquals("movie", bodies[0]["type"]?.jsonPrimitive?.content)
            assertTrue(controller.state.value.history.single().watched)
        } finally { controller.job?.cancelAndJoin(); controller.connection?.close() }
    }

    @Test fun markUnwatchedAndASavedPositionSendTheExpectedBodies() = runBlocking<Unit> {
        val started = display("movie-1", watched = true, position = 100.0).copy(resumeActive = false)
        val bodies = mutableListOf<JsonObject>()
        val controller = controller { request ->
            when (request.url.encodedPath) {
                "/api/profiles/4/progress/correct" -> {
                    bodies += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    val action = bodies.last()["action"]?.jsonPrimitive?.content
                    json(when (action) {
                        "unwatched" -> correctResponse(0.0, watched = false)
                        else -> correctResponse(24.0, watched = false, resumeActive = true)
                    })
                }
                else -> error("Unexpected ${request.url}")
            }
        }
        controller.mutable.value = controller.state.value.copy(history = listOf(started))
        try {
            controller.correctHistory(started, HistoryCorrection.Unwatched)
            controller.settleRun()
            controller.correctHistory(started, HistoryCorrection.Position(24))
            controller.settleRun()
            assertEquals(2, bodies.size)
            assertEquals("unwatched", bodies[0]["action"]?.jsonPrimitive?.content)
            assertEquals("position", bodies[1]["action"]?.jsonPrimitive?.content)
            assertEquals(24L, bodies[1]["position"]?.jsonPrimitive?.long)
            assertEquals(24.0, controller.state.value.history.single().position)
            assertTrue(controller.state.value.history.single().resumeActive)
        } finally { controller.job?.cancelAndJoin(); controller.connection?.close() }
    }

    @Test fun aCorrectionCarriesEpisodeCoordinatesInTheBody() = runBlocking<Unit> {
        val episode = card("show:1:2", "series").let { show ->
            show.copy(identity = MediaIdentity("show:1:2", "series", emptyList(), EpisodeCoordinates("aired", 1, 2)),
                parent_identity = MediaIdentity("show", "series", emptyList()))
        }
        val started = HistoryDisplay(HistoryEntry(episode, 1L), "Today", position = 12.0, duration = 48.0, resumeActive = true)
        lateinit var body: JsonObject
        val controller = controller { request ->
            when (request.url.encodedPath) {
                "/api/profiles/4/progress/correct" -> {
                    body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    json(correctResponse(12.0, 48.0, resumeActive = true))
                }
                else -> error("Unexpected ${request.url}")
            }
        }
        controller.mutable.value = controller.state.value.copy(history = listOf(started))
        try {
            controller.correctHistory(started, HistoryCorrection.Position(12))
            controller.settleRun()
            assertEquals("series", body["type"]?.jsonPrimitive?.content)
            assertEquals("show", body["series_id"]?.jsonPrimitive?.content)
            assertEquals(1L, body["season"]?.jsonPrimitive?.long)
            assertEquals(2L, body["episode"]?.jsonPrimitive?.long)
        } finally { controller.job?.cancelAndJoin(); controller.connection?.close() }
    }
}