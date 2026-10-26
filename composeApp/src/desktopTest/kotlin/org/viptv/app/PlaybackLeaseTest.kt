package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlayerFact
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

/**
 * The server forgets a playback lease 60 s after its last heartbeat and never
 * revives it (backend `gateway/playback.rs`: 410 `playback_expired`, later 404).
 * Leaving the app must not cost the viewer their place: a lost lease is
 * re-acquired for the same source at the last position.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackLeaseTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val title = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "metadata", title = "Movie", badges = emptyList())
    private val source = SourceCandidate("opaque-stream", "addon:1", "Fixture")
    private val facts = buildJsonObject { put("platform", "android"); put("can_play_direct", true) }

    /** One fake backend: each `POST /api/playback` opens lease-1, lease-2, … */
    private class Server {
        val opens = mutableListOf<JsonObject>()
        val heartbeats = mutableListOf<String>()
        /** Status for a heartbeat on the given lease; throw for a network failure. */
        var heartbeat: (String) -> HttpStatusCode = { HttpStatusCode.OK }
        /** Status for the given (1-based) open request. */
        var open: (Int) -> HttpStatusCode = { HttpStatusCode.OK }
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
                            server.opens += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                            val status = server.open(server.opens.size)
                            if (status != HttpStatusCode.OK) json("""{"error":"source_not_found"}""", status)
                            else json("""{"id":"lease-${server.opens.size}","status":"ready","renew_after_seconds":20,
                                "delivery":{"kind":"direct","url":"https://media.example/movie-${server.opens.size}.mp4","position":${server.opens.last()["position"]}}}""")
                        }
                        path.endsWith("/heartbeat") -> {
                            val lease = path.removePrefix("/api/playback/").removeSuffix("/heartbeat")
                            server.heartbeats += lease
                            val status = server.heartbeat(lease)
                            json(if (status == HttpStatusCode.OK) "{}" else """{"error":"playback_expired"}""", status)
                        }
                        path.startsWith("/api/playback/") && request.method == HttpMethod.Delete -> json("{}")
                        path.endsWith("/progress") -> json("{}")
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

    /** Open the source and play to [positionMillis]. */
    private fun TestScope.play(controller: ApplicationController, positionMillis: Long = 754_000) {
        controller.playSource(source); runCurrent()
        val input = assertNotNull(controller.state.value.playback)
        controller.observePlaybackFor(input, PlayerFact.Status("Playing"))
        controller.observePlaybackFor(input, PlayerFact.Progress(positionMillis, 1_400_000)); runCurrent()
    }

    /** Android: the Activity pauses the renderer, then the lifecycle reports the app behind. */
    private fun ApplicationController.leaveApp() { suspendPlayback(); appForeground(false) }
    /** Android: onStart resumes the Activity's playback before the lifecycle reports the app in front. */
    private fun ApplicationController.returnToApp() { resumePlayback(); appForeground(true) }

    @Test fun anExpiredLeaseIsReacquiredForTheSameSourceAtTheLastPosition() = runTest {
        val server = Server()
        val controller = controller(server)
        play(controller)
        val first = controller.state.value.playback
        server.heartbeat = { lease -> if (lease == "lease-1") HttpStatusCode.Gone else HttpStatusCode.OK }
        advanceTimeBy(20_001); runCurrent()
        assertEquals(2, server.opens.size)
        assertEquals(listOf("opaque-stream", "opaque-stream"), server.opens.map { it.getValue("stream_id").jsonPrimitive.content })
        assertEquals(754.0, server.opens[1].getValue("position").jsonPrimitive.double)
        assertFalse(server.opens[1].getValue("resume").jsonPrimitive.boolean)
        val state = controller.state.value
        assertEquals(Screen.Player, state.screen)
        assertNull(state.error)
        assertNotSame(first, state.playback)
        assertEquals("lease-2", state.playback?.id)
        assertEquals(754_000L, state.playback?.positionMillis)
        assertFalse(state.paused, "Playback that was playing keeps playing")
        // The new lease is renewed like the first one.
        advanceTimeBy(20_001); runCurrent()
        assertEquals("lease-2", server.heartbeats.last())
        controller.closePlayer()
    }

    @Test fun aNetworkOutageWithinTheLeasesLifeIsRetriedWithoutReopening() = runTest {
        val server = Server()
        val controller = controller(server)
        play(controller)
        val input = controller.state.value.playback
        // Offline from the first heartbeat (20 s) until 45 s: the server lease would still be alive.
        server.heartbeat = { if (testScheduler.currentTime < 45_000) throw java.io.IOException("offline") else HttpStatusCode.OK }
        advanceTimeBy(55_000); runCurrent()
        assertEquals(1, server.opens.size, "An outage the lease outlives must not reopen the source")
        assertEquals(6, server.heartbeats.size, "Backoff 1, 2, 4, 8, 15 s after the first failure")
        assertSame(input, controller.state.value.playback)
        assertNull(controller.state.value.error)
        controller.closePlayer()
    }

    @Test fun aResumeWithoutLeavingTheFrontConfirmsTheLeaseAtOnce() = runTest {
        val server = Server()
        val controller = controller(server)
        play(controller)
        val input = controller.state.value.playback
        advanceTimeBy(5_000)
        // A translucent Activity: pause and resume without stop and start.
        controller.suspendPlayback(); advanceTimeBy(1_000)
        controller.resumePlayback(); runCurrent()
        assertEquals(listOf("lease-1"), server.heartbeats)
        assertSame(input, controller.state.value.playback)
        assertFalse(controller.state.value.paused)
        controller.closePlayer()
    }

    @Test fun aSourceThatCannotBeReacquiredReturnsToSourcesWithAnExplanation() = runTest {
        val server = Server()
        server.open = { attempt -> if (attempt == 1) HttpStatusCode.OK else HttpStatusCode.NotFound }
        server.heartbeat = { HttpStatusCode.Gone }
        val controller = controller(server)
        play(controller)
        advanceTimeBy(20_001); runCurrent()
        assertEquals(2, server.opens.size, "A missing source is not retried")
        val state = controller.state.value
        assertEquals(Screen.Sources, state.screen)
        assertNull(state.playback)
        assertFalse(state.busy)
        assertContains(state.error.orEmpty(), "Choose the source again")
        assertNull(controller.playbackRenewal)
        val heartbeats = server.heartbeats.size
        advanceTimeBy(120_000); runCurrent()
        assertEquals(heartbeats, server.heartbeats.size, "No lease is left renewing")
    }

    @Test fun aPausedAppBehindTheHomeScreenSendsNoRenewalsAndRenewsAtOnceOnReturn() = runTest {
        val server = Server()
        val controller = controller(server)
        play(controller)
        val input = controller.state.value.playback
        advanceTimeBy(5_000)
        controller.leaveApp()
        advanceTimeBy(10 * 60_000); runCurrent()
        assertEquals(emptyList(), server.heartbeats)
        controller.returnToApp(); runCurrent()
        assertEquals(listOf("lease-1"), server.heartbeats, "Returning renews immediately")
        assertSame(input, controller.state.value.playback)
        assertFalse(controller.state.value.paused, "Playback resumes once the lease is confirmed")
        assertEquals(1, server.opens.size)
        controller.closePlayer()
    }

    @Test fun returningAfterTheLeaseLapsedHoldsThePlayerUntilTheSourceIsReacquired() = runTest {
        val server = Server()
        val controller = controller(server)
        play(controller)
        controller.leaveApp()
        advanceTimeBy(5 * 60_000); runCurrent()
        server.heartbeat = { lease -> if (lease == "lease-1") HttpStatusCode.Gone else HttpStatusCode.OK }
        controller.resumePlayback()
        assertTrue(controller.state.value.paused, "The player must not use a stream whose lease may have lapsed")
        controller.appForeground(true); runCurrent()
        assertEquals(2, server.opens.size)
        assertEquals(754.0, server.opens[1].getValue("position").jsonPrimitive.double)
        val state = controller.state.value
        assertEquals(Screen.Player, state.screen)
        assertEquals("lease-2", state.playback?.id)
        assertFalse(state.paused)
        assertNull(state.error)
        controller.closePlayer()
    }

    @Test fun aViewerWhoPausedBeforeLeavingStaysPausedAfterReacquisition() = runTest {
        val server = Server()
        val controller = controller(server)
        play(controller)
        controller.pausePlayback()
        controller.leaveApp()
        advanceTimeBy(5 * 60_000); runCurrent()
        server.heartbeat = { lease -> if (lease == "lease-1") HttpStatusCode.Gone else HttpStatusCode.OK }
        controller.returnToApp(); runCurrent()
        assertEquals("lease-2", controller.state.value.playback?.id)
        assertTrue(controller.state.value.paused)
        controller.closePlayer()
    }

    @Test fun mediaStillPlayingBehindAnotherWindowKeepsItsLease() = runTest {
        val server = Server()
        val controller = controller(server)
        play(controller)
        // Desktop minimise or a hidden browser tab: the player keeps playing.
        controller.appForeground(false)
        advanceTimeBy(60_001); runCurrent()
        assertEquals(listOf("lease-1", "lease-1", "lease-1"), server.heartbeats)
        controller.closePlayer()
    }
}
