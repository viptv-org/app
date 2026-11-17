package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.player.PresentationProof
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class StartupBeforeFirstFrameTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val episode = Card(MediaIdentity("show:1:1", "series", emptyList(), EpisodeCoordinates("aired", 1, 1)), provider_id = "metadata", title = "Episode 1", badges = emptyList())
    private val nextEpisode = episode.copy(identity = MediaIdentity("show:1:2", "series", emptyList(), EpisodeCoordinates("aired", 1, 2)), title = "Episode 2")
    private val source = SourceCandidate("opaque-stream", "addon:1", "Fixture")
    private val facts = buildJsonObject { put("platform", "web"); put("can_play_direct", true) }

    private inner class Host {
        val gates = Channel<Unit>(Channel.UNLIMITED)
        val opened = EngineRequests<Long>()
        private val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            fun json(body: String) = respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            if (path == "/api/playback" && request.method == HttpMethod.Post) {
                val position = Json.parseToJsonElement((request.body as TextContent).text).jsonObject.getValue("position").jsonPrimitive.double
                json("""{"id":"lease","status":"ready","renew_after_seconds":20,
                    "delivery":{"kind":"gateway","url":"https://media.example/scoped/index.m3u8","position":$position,"duration":3600}}""")
            } else json("{}")
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = true)

        fun controller(scope: CoroutineScope): ApplicationController {
            val controller = ApplicationController(scope, store)
            controller.connection = remote
            controller.leaseOpener = PlaybackLeaseOpener { connection, candidate, profile, forceGateway, position, stage ->
                gates.receive()
                connection.openPlayback(candidate, profile, facts, forceGateway, position, torrents = null, stage = stage)
                    .also { opened += it.input.positionMillis }
            }
            controller.mutable.value = ApplicationState(screen = Screen.Sources, selectedProfile = "profile",
                sources = listOf(source), sourceTitle = episode, preferences = PreferencesSnapshot())
            return controller
        }

        fun close(controller: ApplicationController) {
            controller.job?.cancel()
            controller.releaseRemotePlayback()
            remote.close()
        }
    }

    private suspend fun until(what: String, condition: () -> Boolean) {
        try { withTimeout(5_000) { while (!condition()) delay(5) } } catch (_: TimeoutCancellationException) { fail("timed out waiting for $what") }
    }

    private suspend fun ApplicationController.startAndPresent(host: Host, previous: PlaybackInput? = null) {
        host.gates.send(Unit)
        until("the player") { state.value.playback.let { it != null && it !== previous } }
        assertNotNull(state.value.startup, "the opening screen shows before the first frame")
        observePlaybackFor(state.value.playback, PlayerFact.Presentation(PresentationProof.DesktopDrawCompleted))
        assertNull(state.value.startup, "the first frame clears the opening screen")
    }

    @Test fun seeksThatReopenTheStreamNeverBringTheOpeningScreenBackAndTheLastTargetWins() = runBlocking<Unit> {
        val host = Host()
        val controller = host.controller(this)
        val shown = mutableListOf<StartupProgress>()
        val watcher = launch { controller.state.collect { it.startup?.let(shown::add) } }
        try {
            controller.playSource(source)
            controller.startAndPresent(host)
            yield(); shown.clear()

            val seek = assertNotNull(controller.state.value.playback?.remoteSeek, "a gateway stream reopens to seek")
            seek(600_000)
            until("the first reopen to start") { controller.state.value.playback == null }
            seek(1_200_000)
            seek(1_800_000)
            repeat(3) { host.gates.send(Unit) }
            until("the latest target to open") { controller.state.value.playback?.positionMillis == 1_800_000L }
            yield()
            assertEquals(listOf(1_800_000L), host.opened.drop(1), "every seek is accepted and only the latest target opens")
            assertTrue(shown.isEmpty(), "a reopen after the first frame never shows the opening screen: $shown")
            assertNull(controller.state.value.startup)
        } finally { watcher.cancel(); host.close(controller) }
    }

    @Test fun aNewEpisodeOrANewPlaybackStartsWithTheOpeningScreenAgain() = runBlocking<Unit> {
        val host = Host()
        val controller = host.controller(this)
        try {
            controller.playSource(source)
            controller.startAndPresent(host)

            val first = controller.state.value.playback
            controller.updateState { it.copy(sourceTitle = nextEpisode) }
            controller.openSource(source, forceGateway = false)
            assertNotNull(controller.state.value.startup, "another episode opens with the opening screen")
            controller.startAndPresent(host, first)

            controller.closePlayer()
            controller.updateState { it.copy(sources = listOf(source)) }
            controller.playSource(source)
            assertNotNull(controller.state.value.startup, "playing the same source again after closing opens with the opening screen")
            controller.startAndPresent(host)
        } finally { host.close(controller) }
    }
}
