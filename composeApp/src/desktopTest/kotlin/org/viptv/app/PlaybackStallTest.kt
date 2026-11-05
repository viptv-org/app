package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackStallTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val title = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "metadata", title = "Movie", badges = emptyList())
    private val source = SourceCandidate("opaque-stream", "addon:1", "Fixture")
    private val facts = buildJsonObject { put("platform", "android"); put("can_play_direct", true) }

    private class Server {
        val opens = mutableListOf<JsonObject>()
        val progress = mutableListOf<JsonObject>()
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
                            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                            server.opens += body
                            json("""{"id":"lease-${server.opens.size}","status":"ready","renew_after_seconds":20,
                                "delivery":{"kind":"direct","url":"https://media.example/movie-${server.opens.size}.mp4","position":${body.getValue("position")}}}""")
                        }
                        path.endsWith("/heartbeat") -> json("{}")
                        path.startsWith("/api/playback/") && request.method == HttpMethod.Delete -> json("{}")
                        path.endsWith("/progress") -> {
                            server.progress += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                            json("{}")
                        }
                        else -> json("{}", HttpStatusCode.NotFound)
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

    private fun TestScope.play(controller: ApplicationController, positionMillis: Long = 754_000): PlaybackInput {
        controller.playSource(source); runCurrent()
        val input = assertNotNull(controller.state.value.playback)
        controller.observePlaybackFor(input, PlayerFact.Status("Playing"))
        controller.observePlaybackFor(input, PlayerFact.Progress(positionMillis, 1_400_000)); runCurrent()
        return input
    }

    private val stalled = "$FALLBACK_REASON_FAILED. Choose another source."

    @Test fun aWaitingStreamWithoutProgressFailsAfterTheWindow() = runTest {
        val server = Server()
        val controller = controller(server)
        val input = play(controller)
        controller.observePlaybackFor(input, PlayerFact.Status("Buffering"))
        advanceTimeBy(PLAYBACK_STALL_WINDOW_MILLIS - 1); runCurrent()
        assertNull(controller.state.value.error, "The stall window has not closed yet")
        advanceTimeBy(2); runCurrent()
        val state = controller.state.value
        assertEquals(stalled, state.error)
        assertSame(input, state.playback, "The failure is shown over the player, not on the source list")
        assertEquals(Screen.Player, state.screen)
        controller.closePlayer()
    }

    @Test fun progressInsideTheWindowRestartsIt() = runTest {
        val server = Server()
        val controller = controller(server)
        val input = play(controller)
        controller.observePlaybackFor(input, PlayerFact.Status("Buffering"))
        advanceTimeBy(20_000); runCurrent()
        controller.observePlaybackFor(input, PlayerFact.Progress(760_000, 1_400_000)); runCurrent()
        advanceTimeBy(PLAYBACK_STALL_WINDOW_MILLIS - 1); runCurrent()
        assertNull(controller.state.value.error, "A clock that advances is not a stall")
        advanceTimeBy(2); runCurrent()
        assertEquals(stalled, controller.state.value.error)
        controller.closePlayer()
    }

    @Test fun aSettledStatusCancelsTheDeadline() = runTest {
        val server = Server()
        val controller = controller(server)
        val input = play(controller)
        controller.observePlaybackFor(input, PlayerFact.Status("Buffering"))
        advanceTimeBy(10_000); runCurrent()
        controller.observePlaybackFor(input, PlayerFact.Status("Playing")); runCurrent()
        advanceTimeBy(2 * PLAYBACK_STALL_WINDOW_MILLIS); runCurrent()
        assertNull(controller.state.value.error)
        controller.closePlayer()
    }

    @Test fun theDeadlineIsDroppedWhenTheSourceIsReplaced() = runTest {
        val server = Server()
        val controller = controller(server)
        val input = play(controller)
        controller.observePlaybackFor(input, PlayerFact.Status("Buffering"))
        assertNotNull(controller.playbackStall)
        controller.closePlayer(); runCurrent()
        assertNull(controller.playbackStall)
        advanceTimeBy(2 * PLAYBACK_STALL_WINDOW_MILLIS); runCurrent()
        assertNull(controller.state.value.error)
    }

    @Test fun retryAfterAStallSavesThePositionAndReopensTheSameSource() = runTest {
        val server = Server()
        val controller = controller(server)
        val input = play(controller)
        controller.observePlaybackFor(input, PlayerFact.Status("Buffering"))
        advanceTimeBy(PLAYBACK_STALL_WINDOW_MILLIS + 1); runCurrent()
        assertEquals(stalled, controller.state.value.error)
        controller.retrySource(source); runCurrent()
        assertEquals(2, server.opens.size)
        assertEquals(listOf("opaque-stream", "opaque-stream"), server.opens.map { it.getValue("stream_id").jsonPrimitive.content })
        assertTrue(server.opens[1].getValue("resume").jsonPrimitive.boolean, "Retry resumes where the viewer was, not from the start")
        val record = Json.decodeFromJsonElement<PlaybackRecord>(server.progress.last().getValue("record"))
        assertEquals(754_000L, record.position_millis)
        val state = controller.state.value
        assertNull(state.error)
        assertEquals(Screen.Player, state.screen)
        assertEquals("lease-2", state.playback?.id)
        controller.closePlayer()
    }
}