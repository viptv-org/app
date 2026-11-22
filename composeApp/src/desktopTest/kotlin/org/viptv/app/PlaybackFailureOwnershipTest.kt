package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.features.player.PlaybackFailure
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.player.PresentationProof
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackFailureOwnershipTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val title = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "metadata", title = "Movie", badges = emptyList())
    private val source = SourceCandidate("opaque-stream", "addon:1", "Fixture")
    private val facts = buildJsonObject { put("platform", "android"); put("can_play_direct", true) }

    private class Server {
        var opens = 0
        val failures = mutableListOf<JsonObject>()
        var openStatus = HttpStatusCode.OK
    }

    private fun TestScope.controller(server: Server): ApplicationController {
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    val path = request.url.encodedPath
                    fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
                        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                    when {
                        path == "/api/playback" && request.method == HttpMethod.Post -> {
                            if (server.openStatus != HttpStatusCode.OK) return@addHandler json("""{"error":"source_unavailable"}""", server.openStatus)
                            server.opens++
                            json("""{"id":"lease-${server.opens}","status":"ready","renew_after_seconds":20,
                                "delivery":{"kind":"direct","url":"https://media.example/movie-${server.opens}.mp4","position":0}}""")
                        }
                        path == PLAYBACK_FAILURES_PATH -> {
                            server.failures += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                            json("{}")
                        }
                        else -> json("{}")
                    }
                }
            }
            configureTransport()
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(backgroundScope, store)
        controller.connection = remote
        controller.leaseOpener = PlaybackLeaseOpener { connection, candidate, profile, forceGateway, position, stage ->
            connection.openPlayback(candidate, profile, facts, forceGateway, position, torrents = null, stage = stage)
        }
        controller.mutable.value = ApplicationState(screen = Screen.Sources, selectedProfile = "profile", sources = listOf(source),
            sourceTitle = title, preferences = PreferencesSnapshot())
        return controller
    }

    private fun TestScope.play(controller: ApplicationController): PlaybackInput {
        controller.playSource(source); runCurrent()
        val input = assertNotNull(controller.state.value.playback)
        controller.observePlaybackFor(input, PlayerFact.Presentation(PresentationProof.DesktopDrawCompleted))
        controller.observePlaybackFor(input, PlayerFact.Status("Playing"))
        controller.observePlaybackFor(input, PlayerFact.Progress(754_000, 1_400_000)); runCurrent()
        return input
    }

    @Test fun anUnrelatedOperationFailingDuringPlaybackIsNotAPlaybackFailure() = runTest {
        val server = Server()
        val controller = controller(server)
        val input = play(controller)
        controller.run { throw LocalFailure("Could not load the next episode.") }; runCurrent()
        val state = controller.state.value
        assertEquals("Could not load the next episode.", state.error)
        assertNull(state.playbackFailure, "Only the playback itself can fail the player")
        assertSame(input, state.playback)
        assertFalse(state.paused)
        assertTrue(server.failures.isEmpty())
        controller.closePlayer()
    }

    @Test fun aRendererFailureStopsThePlayerAndReportsOneRecord() = runTest {
        val server = Server()
        val controller = controller(server)
        val input = play(controller)
        controller.observePlaybackFor(input, PlayerFact.Failed); runCurrent()
        val state = controller.state.value
        assertEquals(PlaybackFailure.Failed, state.playbackFailure)
        assertTrue(state.paused, "Declaring the failure stops the player in the same step")
        val record = server.failures.single()
        assertEquals("renderer", record.getValue("cause").jsonPrimitive.content)
        assertEquals(754_000L, record.getValue("position_ms").jsonPrimitive.long)
        assertTrue(record.getValue("first_frame").jsonPrimitive.boolean)
        assertEquals("lease-1", record.getValue("playback_id").jsonPrimitive.content)
        assertEquals(setOf("cause", "position_ms", "first_frame", "playback_id"), record.keys)
        controller.observePlaybackFor(input, PlayerFact.Progress(760_000, 1_400_000)); runCurrent()
        assertEquals(PlaybackFailure.Failed, controller.state.value.playbackFailure, "A renderer that failed is not disproved by a late tick")
        controller.closePlayer()
    }

    @Test fun progressAfterAStallClearsTheFailure() = runTest {
        val server = Server()
        val controller = controller(server)
        val input = play(controller)
        controller.observePlaybackFor(input, PlayerFact.Status("Buffering"))
        advanceTimeBy(PLAYBACK_STALL_WINDOW_MILLIS + 1); runCurrent()
        assertEquals(PlaybackFailure.Failed, controller.state.value.playbackFailure)
        assertTrue(controller.state.value.paused)
        assertEquals("stall", server.failures.single().getValue("cause").jsonPrimitive.content)
        controller.observePlaybackFor(input, PlayerFact.Progress(758_000, 1_400_000)); runCurrent()
        val state = controller.state.value
        assertNull(state.playbackFailure, "Evidence of playback wins over a declared stall")
        assertFalse(state.paused)
        assertEquals(758_000L, input.positionMillis, "The renderer resumes where the evidence left it")
        controller.closePlayer()
    }

    @Test fun aFailureReportedForAReplacedPlaybackIsIgnored() = runTest {
        val server = Server()
        val controller = controller(server)
        val first = play(controller)
        controller.retrySource(source); runCurrent()
        assertNotSame(first, controller.state.value.playback)
        controller.observePlaybackFor(first, PlayerFact.Failed); runCurrent()
        assertNull(controller.state.value.playbackFailure)
        assertTrue(server.failures.isEmpty())
        controller.closePlayer()
    }

    @Test fun anOpenFailureOnThePlayerIsOwnedByThatOpen() = runTest {
        val server = Server()
        val controller = controller(server)
        play(controller)
        server.openStatus = HttpStatusCode.BadGateway
        controller.retrySource(source); runCurrent()
        assertEquals(PlaybackFailure.Failed, controller.state.value.playbackFailure)
        val record = server.failures.single()
        assertEquals("open_failed", record.getValue("cause").jsonPrimitive.content)
        assertEquals(502, record.getValue("status").jsonPrimitive.int)
        assertFalse(record.containsKey("playback_id"))
        controller.closePlayer()
        assertNull(controller.state.value.playbackFailure)
    }
}
