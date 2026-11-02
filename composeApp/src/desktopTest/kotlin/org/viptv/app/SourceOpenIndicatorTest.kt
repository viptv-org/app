package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlayerFact
import org.viptv.app.player.PresentationProof
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class SourceOpenIndicatorTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val title = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "metadata", title = "Movie", badges = emptyList())
    private val source = SourceCandidate("opaque-stream", "addon:1", "Fixture")
    private val facts = buildJsonObject { put("platform", "web"); put("can_play_direct", true) }

    private inner class Host(val openStatus: HttpStatusCode = HttpStatusCode.OK) {
        val gate = CompletableDeferred<Unit>()
        val opens = java.util.concurrent.atomic.AtomicInteger(0)
        private val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            when {
                path == "/api/playback" && request.method == HttpMethod.Post ->
                    if (openStatus == HttpStatusCode.OK)
                        json("""{"id":"lease-1","status":"ready","renew_after_seconds":20,
                            "delivery":{"kind":"direct","url":"https://media.example/movie.mp4","position":0}}""")
                    else json("""{"error":"source_not_found"}""", openStatus)
                path.endsWith("/first-frame") -> json("{}")
                path.endsWith("/playback-events") -> json("""{"state":"recorded","completed":false,"action":"start"}""")
                path.startsWith("/api/playback/") && request.method == HttpMethod.Delete -> json("{}")
                path.endsWith("/progress") -> json("{}")
                else -> json("{}", HttpStatusCode.NotFound)
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = true)

        fun controller(scope: CoroutineScope): ApplicationController {
            val controller = ApplicationController(scope, store)
            controller.connection = remote
            controller.leaseOpener = PlaybackLeaseOpener { connection, candidate, profile, forceGateway, position, stage ->
                gate.await()
                connection.openPlayback(candidate, profile, facts, forceGateway, position, torrents = null, stage = stage)
                    .also { opens.incrementAndGet() }
            }
            controller.mutable.value = ApplicationState(screen = Screen.Sources, selectedProfile = "profile",
                sources = listOf(source), sourceTitle = title, preferences = PreferencesSnapshot())
            return controller
        }

        fun close(controller: ApplicationController) {
            controller.job?.cancel()
            controller.cancelSourceDetail()
            controller.releaseRemotePlayback()
            remote.close()
        }
    }

    private suspend fun until(what: String, condition: () -> Boolean) {
        try { withTimeout(5_000) { while (!condition()) delay(5) } } catch (_: TimeoutCancellationException) { fail("timed out waiting for $what") }
    }

    @Test fun aSlowOpenShowsOpeningStatusFromSelectionThroughFirstFrame() = runBlocking<Unit> {
        val host = Host()
        val controller = host.controller(this)
        try {
            controller.playSource(source)
            val selected = controller.state.value
            assertEquals(source, selected.playingSource, "the tapped source is acknowledged immediately")
            assertEquals(source, selected.openingSource, "and carries an opening state")
            assertTrue(selected.busy, "the selector is busy while the source opens")
            assertEquals(Screen.Sources, selected.screen, "the selector stays up during the open")

            host.gate.complete(Unit)
            until("the player") { controller.state.value.screen == Screen.Player }
            val playing = controller.state.value
            assertEquals(source, playing.openingSource, "the opening state holds through player startup")
            assertNotNull(playing.playback)
            assertFalse(playing.busy)

            controller.observePlaybackFor(playing.playback, PlayerFact.Presentation(PresentationProof.DesktopDrawCompleted))
            assertNull(controller.state.value.openingSource, "the first frame clears the opening state")
            assertEquals(1, host.opens.get())
        } finally { host.close(controller) }
    }

    @Test fun duplicateClicksCannotCreateCompetingOpens() = runBlocking<Unit> {
        val host = Host()
        val controller = host.controller(this)
        try {
            controller.playSource(source)
            controller.playSource(source)
            host.gate.complete(Unit)
            until("the player") { controller.state.value.screen == Screen.Player }
            assertEquals(1, host.opens.get(), "a duplicate tap does not start a competing open")
            assertEquals(source, controller.state.value.playingSource)
        } finally { host.close(controller) }
    }

    @Test fun leavingTheSelectorCancelsAStaleOpen() = runBlocking<Unit> {
        val host = Host()
        val controller = host.controller(this)
        try {
            controller.playSource(source)
            assertEquals(source, controller.state.value.openingSource)
            controller.backSources()
            val state = controller.state.value
            assertNull(state.openingSource, "leaving the selector clears the opening state")
            assertFalse(state.busy)
            assertNull(state.playback)

            host.gate.complete(Unit)
            delay(50)
            assertNull(controller.state.value.playback, "the cancelled open never delivers a player")
            assertEquals(0, host.opens.get())
        } finally { host.close(controller) }
    }

    @Test fun aFailedOpenClearsTheOpeningStateAndLeavesControlsUsable() = runBlocking<Unit> {
        val host = Host(openStatus = HttpStatusCode.NotFound)
        val controller = host.controller(this)
        try {
            controller.playSource(source)
            assertEquals(source, controller.state.value.openingSource)
            assertTrue(controller.state.value.busy)
            host.gate.complete(Unit)
            until("the error") { controller.state.value.error != null }
            val state = controller.state.value
            assertNull(state.openingSource, "a failure clears the opening state")
            assertFalse(state.busy, "and re-enables the source rows")
            assertEquals(Screen.Sources, state.screen)
            assertNull(state.playback)
        } finally { host.close(controller) }
    }
}