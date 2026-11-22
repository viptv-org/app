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
class HeartbeatStatsSourceTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val title = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "metadata", title = "Movie", badges = emptyList())
    private val source = SourceCandidate("opaque-stream", "addon:1", "Fixture")
    private val facts = buildJsonObject { put("platform", "android"); put("can_play_direct", true) }
    private val direct = """{"id":"lease","status":"ready","renew_after_seconds":20,"delivery":{"kind":"direct","url":"https://media.example/movie.mp4"}}"""
    private val nativeTorrent = """{"id":"lease","status":"ready","renew_after_seconds":20,
        "delivery":{"kind":"native_torrent","live":false,"format":"original","position":0,"grant":{}}}"""

    private class Torrents : LocalTorrents {
        val renewals = EngineRequests<JsonObject?>()
        override val capability = buildJsonObject { put("version", 2) }
        override suspend fun negotiate(remote: RemoteConnection) = true
        override suspend fun acquire(remote: RemoteConnection, id: String, request: JsonObject, stage: (String) -> Unit) = object : LocalTorrentPlayback {
            override val mediaUrl = "http://127.0.0.1:41999/media/private"
            override val positionSeconds = 0.0
            override suspend fun renew(stats: JsonObject?) { renewals += stats }
            override suspend fun firstFrame() = Unit
            override suspend fun close() = Unit
        }
    }

    private fun TestScope.controller(ready: String, heartbeats: EngineRequests<String>, torrents: LocalTorrents?): ApplicationController {
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    val path = request.url.encodedPath
                    if (path.endsWith("/heartbeat")) heartbeats += (request.body as? TextContent)?.text.orEmpty()
                    respond(if (path == "/api/playback" && request.method == HttpMethod.Post) ready else "{}",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            configureTransport()
        }
        val controller = ApplicationController(backgroundScope, store)
        controller.connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
        controller.leaseOpener = PlaybackLeaseOpener { connection, candidate, profile, forceGateway, position, stage ->
            connection.openPlayback(candidate, profile, facts, forceGateway, position, torrents = torrents, stage = stage)
        }
        controller.mutable.value = ApplicationState(screen = Screen.Sources, selectedProfile = "profile", sources = listOf(source),
            sourceTitle = title, preferences = PreferencesSnapshot())
        return controller
    }

    private fun TestScope.stallOnRenderedInput(controller: ApplicationController) {
        controller.playSource(source); runCurrent()
        val rendered = assertNotNull(controller.state.value.playback)
        controller.observePlaybackFor(rendered, PlayerFact.Status("Playing")); runCurrent()
        rendered.stats.stalled(true)
        rendered.stats.stalled(false)
        advanceTimeBy(20_001); runCurrent()
    }

    @Test fun aStallTheRendererReportsReachesTheRemoteHeartbeat() = runTest {
        val heartbeats = EngineRequests<String>()
        val controller = controller(direct, heartbeats, torrents = null)
        stallOnRenderedInput(controller)
        val buffering = Json.parseToJsonElement(heartbeats.last()).jsonObject.getValue("buffering").jsonObject
        assertEquals(1, buffering.getValue("rebuffer_count").jsonPrimitive.int)
        controller.closePlayer()
    }

    @Test fun aStallTheRendererReportsReachesTheNativeTorrentRenewal() = runTest {
        val torrents = Torrents()
        val controller = controller(nativeTorrent, EngineRequests(), torrents)
        stallOnRenderedInput(controller)
        assertEquals(1, assertNotNull(torrents.renewals.last()).getValue("rebuffer_count").jsonPrimitive.int)
        controller.closePlayer()
    }
}
