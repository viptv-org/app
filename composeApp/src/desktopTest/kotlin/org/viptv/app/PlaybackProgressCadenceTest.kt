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

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackProgressCadenceTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val episode = Card(
        identity = MediaIdentity("tt12728856:1:11", "series", emptyList(), EpisodeCoordinates("aired", 1, 11)),
        provider_id = "addon:4", title = "Noir's Decision", badges = emptyList(),
        parent_identity = MediaIdentity("tt12728856", "series", listOf(ExternalId("imdb", "tt12728856"))),
        metadata_origin = MetadataOrigin("stremio", "addon:4", "incarnation"))
    private val source = SourceCandidate("opaque-stream", "addon:4", "Fixture")
    private val android = buildJsonObject { put("platform", "android"); put("can_play_direct", true) }

    private class Server {
        val progress = EngineRequests<JsonObject>()
        val heartbeats = EngineRequests<String>()
        val stops = EngineRequests<String>()
        var heartbeat: (String) -> HttpStatusCode = { HttpStatusCode.OK }
        var opens = 0
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
                            server.opens++
                            json("""{"id":"lease-${server.opens}","status":"ready","renew_after_seconds":20,
                                "delivery":{"kind":"direct","url":"https://media.example/episode-${server.opens}.mkv","position":0}}""")
                        }
                        path.endsWith("/heartbeat") -> {
                            val lease = path.removePrefix("/api/playback/").removeSuffix("/heartbeat")
                            server.heartbeats += lease
                            val status = server.heartbeat(lease)
                            json(if (status == HttpStatusCode.OK) "{}" else """{"error":"playback_expired"}""", status)
                        }
                        path.startsWith("/api/playback/") && request.method == HttpMethod.Delete -> { server.stops += path; json("{}") }
                        path == "/api/profiles/profile/progress" && request.method == HttpMethod.Put -> {
                            server.progress += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                            json("""{"ok":true}""")
                        }
                        else -> json("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            configureTransport()
        }
        val controller = ApplicationController(backgroundScope, store, freshness = Freshness(testScheduler.timeSource))
        controller.connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
        controller.leaseOpener = PlaybackLeaseOpener { connection, candidate, profile, forceGateway, position, stage ->
            connection.openPlayback(candidate, profile, android, forceGateway, position, torrents = null, stage = stage)
        }
        controller.mutable.value = ApplicationState(screen = Screen.Sources, selectedProfile = "profile", sources = listOf(source),
            sourceTitle = episode, preferences = PreferencesSnapshot())
        return controller
    }

    private fun TestScope.playFor(controller: ApplicationController, fromMillis: Long, untilMillis: Long) {
        var position = fromMillis
        while (position < untilMillis) {
            controller.state.value.playback?.let { controller.observePlaybackFor(it, PlayerFact.Progress(position, 1_452_620)) }
            advanceTimeBy(200); runCurrent()
            position += 200
        }
    }

    private fun JsonObject.position() = getValue("record").jsonObject.getValue("position_millis").jsonPrimitive.long

    @Test fun androidPlaybackSavesPositionEveryIntervalWithoutAStop() = runTest {
        val server = Server()
        val controller = controller(server)
        controller.playSource(source); runCurrent()
        val input = assertNotNull(controller.state.value.playback)
        controller.observePlaybackFor(input, PlayerFact.Status("Playing"))
        playFor(controller, 88_000, 88_000 + 61_000)

        val saved = server.progress.map { it.position() }
        assertEquals(4, saved.size, "One write per ${PROGRESS_SAVE_INTERVAL} of playback, got $saved")
        saved.zipWithNext { a, b -> assertTrue(b - a in PROGRESS_SAVE_INTERVAL.inWholeMilliseconds..PROGRESS_SAVE_INTERVAL.inWholeMilliseconds + 200, "Writes $saved") }
        assertTrue(saved.last() >= 88_000 + 60_000 - PROGRESS_SAVE_INTERVAL.inWholeMilliseconds)
        val record = Json.decodeFromJsonElement<PlaybackRecord>(server.progress.last().getValue("record"))
        assertEquals(episode.identity, record.identity)
        assertEquals(episode.parent_identity, record.parent_identity)
        assertEquals(episode.metadata_origin, record.metadata_origin)
        assertEquals(1_452_620L, record.duration_millis)
        assertFalse(server.progress.last().getValue("ended").jsonPrimitive.boolean)
        assertTrue(server.heartbeats.isNotEmpty(), "The lease is renewed alongside the writes")
        assertEquals(emptyList(), server.stops, "Nothing closed the session")
        assertSame(input, controller.state.value.playback)
    }

    @Test fun anExpiredLeaseSavesTheLastPositionBeforeReopening() = runTest {
        val server = Server()
        val controller = controller(server)
        controller.playSource(source); runCurrent()
        val input = assertNotNull(controller.state.value.playback)
        controller.observePlaybackFor(input, PlayerFact.Status("Playing"))
        playFor(controller, 900_000, 900_000 + 19_000)
        val periodic = server.progress.size
        assertEquals(1, periodic)

        server.heartbeat = { lease -> if (lease == "lease-1") HttpStatusCode.Gone else HttpStatusCode.OK }
        advanceTimeBy(1_200); runCurrent()

        assertEquals(2, server.opens, "The expired lease is reacquired")
        assertEquals(periodic + 1, server.progress.size, "Expiry writes the position it reached")
        assertEquals(918_800L, server.progress.last().position())
        assertFalse(server.progress.last().getValue("ended").jsonPrimitive.boolean)
        assertEquals("lease-2", controller.state.value.playback?.id)
        controller.closePlayer()
    }
}
