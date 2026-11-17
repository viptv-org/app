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
class LeaseExpiryResumeTest {
    private class Store : RemoteSessionStore {
        var saved: SavedRemoteSession? = null
        override suspend fun read(): SavedRemoteSession? = saved
        override suspend fun save(session: SavedRemoteSession) { saved = session }
        override suspend fun clear() { saved = null }
    }

    private val episode = Card(
        identity = MediaIdentity("tt12728856:1:11", "series", emptyList(), EpisodeCoordinates("aired", 1, 11)),
        provider_id = "addon:4", title = "Noir's Decision", badges = emptyList(),
        parent_identity = MediaIdentity("tt12728856", "series", listOf(ExternalId("imdb", "tt12728856"))),
        metadata_origin = MetadataOrigin("stremio", "addon:4", "incarnation"))
    private val android = buildJsonObject { put("platform", "android"); put("can_play_direct", true) }

    private data class Stream(val label: String, val detail: String, val binge: String, val size: Long, val fingerprint: String = detail.hashCode().toString())
    private val animeTime = Stream("Torrentio\n1080p", "[Anime Time] Hidden Dungeon - 11 [Dual Audio][BD][1080p]", "torrentio|anime-time|1080p", 1_400_000_000)
    private val animeTimeV2 = Stream("Torrentio\n1080p", "[Anime Time] Hidden Dungeon - 11v2 [Dual Audio][BD][1080p]", "torrentio|anime-time|1080p", 1_410_000_000)
    private val subsPlease = Stream("Torrentio\n1080p", "[SubsPlease] Hidden Dungeon - 11 (1080p)", "torrentio|subsplease|1080p", 1_300_000_000)
    private val animeTimeRelabelled = Stream("Torrentio+\n1080p BD", "Hidden Dungeon S01E11 1080p", "torrentio|renamed|1080p", 1_400_000_001, animeTime.fingerprint)
    private val other = Stream("Torrentio\n720p", "[Other] Hidden Dungeon - 11 [720p]", "torrentio|other|720p", 700_000_000)

    private inner class Server(var offered: List<Stream>) {
        val grants = mutableMapOf<String, Stream>()
        val opens = EngineRequests<JsonObject>()
        val failedOpens = EngineRequests<String>()
        var discoveries = 0
        var heartbeat: (String) -> HttpStatusCode = { HttpStatusCode.OK }
        var access = "access-1"
        val refreshes = EngineRequests<String>()
        var bestMatch: Stream? = null
        var fingerprints = true

        fun expireEverything() { grants.clear(); access = "access-2" }

        fun streams(): String {
            discoveries++
            val issued = offered.mapIndexed { index, stream -> "grant-$discoveries-$index".also { grants[it] = stream } to stream }
            return issued.joinToString(",") { (id, s) ->
                """{"id":"$id","name":"${s.label.replace("\n", "\\n")}","detail":"${s.detail}","source_binge_group":"${s.binge}",${if (fingerprints) "\"source_fingerprint\":\"${s.fingerprint}\"," else ""}"presentation":{"size_bytes":${s.size}}}"""
            }
        }
    }

    private fun TestScope.controller(server: Server, store: Store = Store()): ApplicationController {
        var lastIssued = ""
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    val path = request.url.encodedPath
                    fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
                        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                    fun body() = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    if (path == "/api/auth/device/login") return@addHandler json("""{"access_token":"${server.access}","refresh_token":"refresh-1"}""")
                    if (path == "/api/auth/device/refresh") {
                        server.refreshes += body().getValue("refresh_token").jsonPrimitive.content
                        return@addHandler json("""{"access_token":"${server.access}","refresh_token":"refresh-${server.refreshes.size + 1}"}""")
                    }
                    if (request.headers[HttpHeaders.Authorization] != "Bearer ${server.access}") return@addHandler json("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized)
                    when {
                        path == "/api/bootstrap" -> json("""{"identity":{"profiles":[],"profile_id":null},"home":null}""")
                        path == "/api/streams" && request.method == HttpMethod.Post -> json("""{"id":"job"}""")
                        path == "/api/streams/job" -> { lastIssued = server.streams(); json("""{"done":true,"events":[{"seq":1,"source":"addon:4","streams":[$lastIssued]}]}""") }
                        path.endsWith("/source-ranking") -> {
                            val ids = body().getValue("ids").jsonArray.map { it.jsonPrimitive.content }
                            val best = server.bestMatch?.let { wanted -> ids.firstOrNull { server.grants[it] == wanted } } ?: ids.first()
                            json("""{"streams":[${ids.joinToString(",") { """{"id":"$it"}""" }}],"best_match_ids":["$best"],"best_match_id":"$best"}""")
                        }
                        path == "/api/playback" && request.method == HttpMethod.Post -> {
                            val start = body()
                            val id = start.getValue("stream_id").jsonPrimitive.content
                            if (id !in server.grants) { server.failedOpens += id; json("""{"error":"This source or playback session is unavailable in your account. Refresh the sources.","error_code":"source_not_found"}""", HttpStatusCode.NotFound) }
                            else {
                                server.opens += start
                                json("""{"id":"lease-${server.opens.size}","status":"ready","renew_after_seconds":20,
                                    "delivery":{"kind":"direct","url":"https://media.example/episode-${server.opens.size}.mkv","position":${start["position"]}}}""")
                            }
                        }
                        path.endsWith("/heartbeat") -> {
                            val status = server.heartbeat(path.removePrefix("/api/playback/").removeSuffix("/heartbeat"))
                            json(if (status == HttpStatusCode.OK) "{}" else """{"error":"This source or playback session is unavailable in your account. Refresh the sources.","error_code":"playback_not_found"}""", status)
                        }
                        path.startsWith("/api/playback/") && request.method == HttpMethod.Delete -> json("{}")
                        path.endsWith("/progress") -> json("""{"ok":true}""")
                        else -> json("{}", HttpStatusCode.NotFound)
                    }
                }
            }
            configureTransport()
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(backgroundScope, store)
        controller.connection = remote
        controller.capability = { DeviceCapability() }
        controller.leaseOpener = PlaybackLeaseOpener { connection, candidate, profile, forceGateway, position, stage ->
            connection.openPlayback(candidate, profile, android, forceGateway, position, torrents = null, stage = stage)
        }
        return controller
    }

    private suspend fun TestScope.discoverAndPlay(controller: ApplicationController, server: Server, choice: Stream, positionMillis: Long) {
        val remote = requireNotNull(controller.connection)
        remote.login("member", "password")
        val ranked = remote.rankSources("profile", remote.sources(episode).candidates)
        controller.mutable.value = ApplicationState(screen = Screen.Sources, selectedProfile = "profile", sources = ranked.rows,
            sourceTitle = episode, preferences = PreferencesSnapshot())
        controller.playSource(ranked.rows.first { server.grants[it.id] == choice }); runCurrent()
        val input = assertNotNull(controller.state.value.playback)
        controller.observePlaybackFor(input, PlayerFact.Status("Playing"))
        controller.observePlaybackFor(input, PlayerFact.Progress(positionMillis, 1_452_620)); runCurrent()
    }

    private fun TestScope.leaveForAnHour(controller: ApplicationController, server: Server) {
        controller.suspendPlayback(); controller.appForeground(false); runCurrent()
        advanceTimeBy(59 * 60_000); runCurrent()
        server.expireEverything()
        server.heartbeat = { HttpStatusCode.NotFound }
    }

    private fun TestScope.returnToApp(controller: ApplicationController) {
        controller.resumePlayback(); controller.appForeground(true); runCurrent()
    }

    @Test fun returningAfterExpiryStartsTheSameStreamAtTheSavedPosition() = runTest {
        val server = Server(listOf(other, animeTime))
        val controller = controller(server)
        discoverAndPlay(controller, server, animeTime, 971_324)
        leaveForAnHour(controller, server)
        returnToApp(controller)

        assertEquals(1, server.failedOpens.size, "The expired grant is tried once, then found again: ${server.failedOpens}")
        assertEquals(2, server.opens.size)
        val reopened = server.opens.last()
        assertEquals(animeTime, server.grants[reopened.getValue("stream_id").jsonPrimitive.content])
        assertEquals(971.324, reopened.getValue("position").jsonPrimitive.double)
        val state = controller.state.value
        assertEquals(Screen.Player, state.screen)
        assertNull(state.error)
        assertFalse(state.paused)
        assertEquals("lease-2", state.playback?.id)
        assertEquals(animeTime.detail, state.playingSource?.detail)
        assertTrue(state.sources.all { it.id in server.grants }, "The picker holds only live grants")
        controller.closePlayer()
    }

    @Test fun aStreamThatIsGoneFallsBackToBestMatchAtTheSavedPosition() = runTest {
        val server = Server(listOf(other, animeTime))
        val controller = controller(server)
        discoverAndPlay(controller, server, animeTime, 600_000)
        leaveForAnHour(controller, server)
        server.offered = listOf(subsPlease, other)
        server.bestMatch = other
        returnToApp(controller)

        assertEquals(2, server.opens.size)
        assertEquals(other, server.grants[server.opens.last().getValue("stream_id").jsonPrimitive.content])
        assertEquals(600.0, server.opens.last().getValue("position").jsonPrimitive.double)
        assertEquals(Screen.Player, controller.state.value.screen)
        assertNull(controller.state.value.error)
        controller.closePlayer()
    }

    @Test fun aReleaseFromTheSameBingeGroupIsPreferredOverBestMatch() = runTest {
        val server = Server(listOf(other, animeTime))
        val controller = controller(server)
        discoverAndPlay(controller, server, animeTime, 300_000)
        leaveForAnHour(controller, server)
        server.offered = listOf(other, animeTimeV2)
        server.bestMatch = other
        returnToApp(controller)

        assertEquals(animeTimeV2, server.grants[server.opens.last().getValue("stream_id").jsonPrimitive.content])
        assertEquals(300.0, server.opens.last().getValue("position").jsonPrimitive.double)
        controller.closePlayer()
    }

    @Test fun theSourceFingerprintOnTheCandidateFindsARelabelledStream() = runTest {
        val server = Server(listOf(other, animeTime))
        val controller = controller(server)
        discoverAndPlay(controller, server, animeTime, 450_000)
        assertEquals(animeTime.fingerprint, controller.state.value.playingSource?.source_fingerprint)
        leaveForAnHour(controller, server)
        server.offered = listOf(other, animeTimeRelabelled)
        server.bestMatch = other
        returnToApp(controller)

        assertEquals(animeTimeRelabelled, server.grants[server.opens.last().getValue("stream_id").jsonPrimitive.content])
        assertEquals(450.0, server.opens.last().getValue("position").jsonPrimitive.double)
        controller.closePlayer()
    }

    @Test fun aLapsedSignInIsRefreshedWithoutTheViewerAndNoStartLoops() = runTest {
        val server = Server(listOf(other, animeTime))
        val controller = controller(server)
        discoverAndPlay(controller, server, animeTime, 971_324)
        leaveForAnHour(controller, server)
        returnToApp(controller)
        advanceTimeBy(5 * 60_000); runCurrent()

        assertEquals(listOf("refresh-1"), server.refreshes, "The lapse costs one refresh, with the stored token")
        assertTrue(server.failedOpens.size <= 1, "No source_not_found loop: ${server.failedOpens}")
        assertEquals(Screen.Player, controller.state.value.screen)
        assertNull(controller.state.value.error)
        controller.closePlayer()
    }

    @Test fun manualPlayFromAStalePickerFindsTheStreamAgain() = runTest {
        val server = Server(listOf(other, animeTime))
        server.fingerprints = false
        val controller = controller(server)
        discoverAndPlay(controller, server, animeTime, 120_000)
        controller.closePlayer(); runCurrent()
        val stale = controller.state.value.sources.first { server.grants[it.id] == animeTime }
        controller.mutable.value = controller.state.value.copy(screen = Screen.Sources, selectedProfile = "profile", sourceTitle = episode, sources = controller.state.value.sources)
        server.grants.clear()
        controller.playSource(stale); runCurrent()

        assertEquals(listOf(stale.id), server.failedOpens.toList())
        assertEquals(animeTime, server.grants[server.opens.last().getValue("stream_id").jsonPrimitive.content])
        assertEquals(Screen.Player, controller.state.value.screen)
        assertNull(controller.state.value.error)
        controller.closePlayer()
    }
}
