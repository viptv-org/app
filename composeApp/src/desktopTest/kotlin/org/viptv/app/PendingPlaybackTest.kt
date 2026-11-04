package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class PendingPlaybackTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val show = "tt5607616"
    private val origin = MetadataOrigin("stremio", "addon:21", "2ed4f1b83f8ca47b35d2dbd6f579fa92")
    private val series = Card(MediaIdentity(show, "series", listOf(ExternalId("imdb", show))), provider_id = "addon:21",
        title = "Re: Zero", badges = emptyList(), metadata_origin = origin)
    private val episode = series.copy(identity = MediaIdentity("$show:4:19", "series", emptyList(),
        EpisodeCoordinates("aired", 4, 19)), parent_identity = series.identity, title = "Episode 19", progress = 0.2)
    private val movie = Card(MediaIdentity("tt0111161", "movie", emptyList()), provider_id = "library", title = "A Movie",
        badges = emptyList(), progress = 0.4)
    private val detail = DetailSnapshot(series, episodes = listOf(episode), next_cursor = null, seasons = emptyList())
    private val stream = SourceCandidate("opaque-stream", "addon:7", "1080p WEB")

    private inner class Host(val streamsHoldMillis: Long = 0, val movieMissing: Boolean = false) {
        val streamRequests = mutableListOf<JsonObject>()
        val playbackRequests = mutableListOf<JsonObject>()
        val metaRequests = mutableListOf<String>()
        val releaseStreams = CompletableDeferred<Unit>()
        private fun MockRequestHandleScope.json(text: String, status: HttpStatusCode = HttpStatusCode.OK) =
            respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))
        private fun body(request: HttpRequestData) = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/api/meta/series/$show" -> { synchronized(metaRequests) { metaRequests += path }; json(Json.encodeToString(detail)) }
                path == "/api/meta/movie/tt0111161" && movieMissing -> json("""{"error":"Title not found","error_code":"metadata_not_found"}""", HttpStatusCode.NotFound)
                path == "/api/meta/movie/tt0111161" -> json(Json.encodeToString(DetailSnapshot(movie.copy(provider_id = "addon:21", metadata_origin = origin), episodes = emptyList())))
                path == "/api/streams" && request.method == HttpMethod.Post -> {
                    synchronized(streamRequests) { streamRequests += body(request) }
                    json("""{"id":"job-1"}""")
                }
                path == "/api/streams/job-1" -> {
                    if (streamsHoldMillis > 0) releaseStreams.await()
                    json("""{"done":true,"events":[{"seq":1,"source":"addon:7","streams":[{"id":"${stream.id}","name":"${stream.label}"}]}]}""")
                }
                path.endsWith("/source-ranking") -> json("""{"streams":[{"id":"${stream.id}"}]}""")
                path == "/api/playback" && request.method == HttpMethod.Post -> {
                    synchronized(playbackRequests) { playbackRequests += body(request) }
                    json("""{"id":"lease-1","status":"ready","renew_after_seconds":20,
                        "delivery":{"kind":"direct","url":"https://media.example/episode.mp4","position":0}}""")
                }
                else -> json("{}", HttpStatusCode.NotFound)
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)

        fun controller(scope: CoroutineScope): ApplicationController {
            val controller = ApplicationController(scope, store)
            controller.connection = remote
            controller.leaseOpener = PlaybackLeaseOpener { connection, candidate, profile, forceGateway, position, stage ->
                connection.openPlayback(candidate, profile, buildJsonObject { put("platform", "android"); put("can_play_direct", true) },
                    forceGateway, position, torrents = null, stage = stage)
            }
            controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "2", preferences = PreferencesSnapshot())
            return controller
        }

        fun close(controller: ApplicationController) {
            releaseStreams.complete(Unit)
            controller.job?.cancel(); controller.cancelSourceDetail(); controller.releaseRemotePlayback()
            remote.close()
        }

        fun streams() = synchronized(streamRequests) { streamRequests.toList() }
    }

    private suspend fun until(what: String, condition: () -> Boolean) {
        try { withTimeout(5_000) { while (!condition()) delay(5) } } catch (_: TimeoutCancellationException) { fail("timed out waiting for $what") }
    }

    @Test fun genericRecordDeduplicatesRevertsAndReplaces() = runBlocking {
        val host = Host()
        val controller = host.controller(this)
        try {
            assertTrue(controller.beginPendingAction("a"))
            assertEquals("a", controller.state.value.pendingAction)
            assertTrue(controller.beginPendingAction("b"), "a different identity replaces the pending one")
            assertEquals("b", controller.state.value.pendingAction)
            assertFalse(controller.beginPendingAction("b"), "starting the pending identity again reverts it")
            assertNull(controller.state.value.pendingAction)
        } finally { host.close(controller) }
    }

    @Test fun tappingASourceRowAndOpeningItClearsThePendingRecord() = runBlocking {
        val host = Host()
        val controller = host.controller(this)
        try {
            controller.playTitle(episode)
            assertEquals(cardLookupKey(episode), controller.state.value.pendingAction)
            until("the source") { controller.state.value.sources.isNotEmpty() && !controller.state.value.busy }
            controller.playSource(controller.state.value.sources.first())
            assertEquals(stream.id, controller.state.value.pendingAction)
            until("the player") { controller.state.value.screen == Screen.Player }
            assertNull(controller.state.value.pendingAction)
        } finally { host.close(controller) }
    }

    @Test fun aSecondTapOnThePendingCardCancelsWithoutARepeatRequest() = runBlocking {
        val host = Host(streamsHoldMillis = 600)
        val controller = host.controller(this)
        try {
            controller.playTitle(episode)
            assertEquals(cardLookupKey(episode), controller.state.value.pendingAction)
            until("the stream job") { host.streams().size == 1 }
            controller.playTitle(episode)
            assertNull(controller.state.value.pendingAction)
            delay(100)
            assertEquals(1, host.streams().size, "the cancelled tap made no second request")
        } finally { host.close(controller) }
    }

    @Test fun aFailedDetailsReadClearsThePendingRecord() = runBlocking {
        val host = Host(movieMissing = true)
        val controller = host.controller(this)
        try {
            controller.playTitle(movie)
            assertNotNull(controller.state.value.pendingAction)
            until("the error") { controller.state.value.error != null }
            assertNull(controller.state.value.pendingAction)
        } finally { host.close(controller) }
    }
}