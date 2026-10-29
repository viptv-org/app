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
class PlayerSettingsVisitTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val title = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "metadata", title = "Movie", badges = emptyList())
    private val source = SourceCandidate("opaque-stream", "addon:1", "Fixture")

    private class Server {
        val opens = mutableListOf<JsonObject>()
        val heartbeats = mutableListOf<String>()
        val released = mutableListOf<String>()
        var heartbeat: (String) -> HttpStatusCode = { HttpStatusCode.OK }
        var preferences = PreferencesSnapshot(revision = 3)
        var gateway = false
        var offsetSeconds = 0.0
    }

    private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
                            val n = server.opens.size
                            if (server.gateway) json("""{"id":"lease-$n","status":"ready","renew_after_seconds":20,
                                "delivery":{"kind":"gateway","format":"hls","url":"https://watch.example/gateway/$n/index.m3u8","position":0,
                                "timeline_offset_seconds":${server.offsetSeconds}}}""")
                            else json("""{"id":"lease-$n","status":"ready","renew_after_seconds":20,
                                "delivery":{"kind":"direct","url":"https://media.example/movie-$n.mp4","position":${server.opens.last()["position"]}}}""")
                        }
                        path.endsWith("/heartbeat") -> {
                            val lease = path.removePrefix("/api/playback/").removeSuffix("/heartbeat")
                            server.heartbeats += lease
                            val status = server.heartbeat(lease)
                            json(if (status == HttpStatusCode.OK) "{}" else """{"error":"playback_expired"}""", status)
                        }
                        path.startsWith("/api/playback/") && request.method == HttpMethod.Delete -> {
                            server.released += path.removePrefix("/api/playback/"); json("{}")
                        }
                        path == "/api/profiles/profile/preferences" && request.method == HttpMethod.Put -> {
                            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                            server.preferences = PreferencesSnapshot(server.preferences.revision + 1,
                                codec.decodeFromJsonElement(body.getValue("preferences")))
                            json(codec.encodeToString(server.preferences))
                        }
                        path == "/api/profiles/profile/preferences" -> json(codec.encodeToString(server.preferences))
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
        val facts = buildJsonObject { put("platform", "android"); put("can_play_direct", !server.gateway) }
        controller.leaseOpener = PlaybackLeaseOpener { connection, candidate, profile, forceGateway, position, stage ->
            connection.openPlayback(candidate, profile, facts, forceGateway, position, torrents = null, stage = stage)
        }
        controller.mutable.value = ApplicationState(screen = Screen.Sources, selectedProfile = "profile", sources = listOf(source),
            sourceTitle = title, preferences = server.preferences, navBack = listOf(Screen.Home), detailReturnScreen = Screen.Home)
        return controller
    }

    private fun TestScope.play(controller: ApplicationController, positionMillis: Long = 754_000) {
        controller.playSource(source); runCurrent()
        val input = assertNotNull(controller.state.value.playback)
        controller.observePlaybackFor(input, PlayerFact.Status("Playing"))
        controller.observePlaybackFor(input, PlayerFact.Progress(positionMillis, 1_400_000)); runCurrent()
    }

    @Test fun theLinkOpensSubtitlesAndAudioSettingsWithPlaybackPausedBehindIt() = runTest {
        val server = Server()
        val controller = controller(server)
        play(controller)
        val input = controller.state.value.playback
        controller.openPlayerSettings(); runCurrent()
        val state = controller.state.value
        assertEquals(Screen.Settings, state.screen)
        assertEquals("Subtitles & audio", state.settingsEntrySection)
        assertTrue(state.settingsReturnsToPlayer)
        assertTrue(state.paused, "Opening Settings pauses playback")
        assertSame(input, state.playback, "Playback waits behind Settings")
        assertEquals(emptyList(), server.released, "The server lease is kept while Settings is open")
        assertEquals(listOf(Screen.Home), state.navBack, "The visit is not a history entry")
        controller.closePlayer()
    }

    @Test fun backReturnsToThePlayerAtTheSamePositionPaused() = runTest {
        val server = Server()
        val controller = controller(server)
        play(controller)
        val input = controller.state.value.playback
        controller.observePlaybackFor(input, PlayerFact.Progress(801_500, 1_400_000))
        controller.openPlayerSettings(); runCurrent()
        controller.navigateBack(); runCurrent()
        val state = controller.state.value
        assertEquals(Screen.Player, state.screen, "Back from the visit goes to the player, not Sources or Home")
        assertSame(input, state.playback)
        assertEquals(801_500L, state.playback?.positionMillis, "The player reopens where the viewer left it")
        assertTrue(state.paused)
        assertFalse(state.settingsReturnsToPlayer)
        assertNull(state.settingsEntrySection)
        assertNull(state.error)
        assertEquals(1, server.opens.size, "Returning does not reopen the source")
        assertEquals(listOf(Screen.Home), state.navBack)
        controller.closePlayer()
    }

    @Test fun aLongSettingsVisitKeepsTheLeaseAlive() = runTest {
        val server = Server()
        val controller = controller(server)
        play(controller)
        val input = controller.state.value.playback
        controller.openPlayerSettings(); runCurrent()
        advanceTimeBy(10 * 60_000); runCurrent()
        assertEquals(30, server.heartbeats.count { it == "lease-1" }, "Heartbeats continue every 20 s while Settings is open")
        controller.navigateBack(); runCurrent()
        val state = controller.state.value
        assertEquals(Screen.Player, state.screen)
        assertSame(input, state.playback)
        assertNull(state.error, "No \"playback session ended\" after a long visit")
        assertEquals(1, server.opens.size)
        assertEquals(emptyList(), server.released)
        controller.closePlayer()
    }

    @Test fun aSubtitleSizeAndColourChangedInSettingsReachThePlayer() = runTest {
        val server = Server()
        val controller = controller(server)
        play(controller)
        val input = assertNotNull(controller.state.value.playback)
        assertEquals(1.0, input.preferences.subtitle_font_scale)
        controller.openPlayerSettings(); runCurrent()
        val current = assertNotNull(controller.state.value.preferences).preferences
        controller.updatePreferences(current.copy(subtitle_font_scale = 1.5, subtitle_text_color = "#FFFF00")); runCurrent()
        assertEquals(1.5, server.preferences.preferences.subtitle_font_scale)
        controller.navigateBack(); runCurrent()
        val playback = assertNotNull(controller.state.value.playback)
        assertSame(input, playback)
        assertEquals(1.5, playback.preferences.subtitle_font_scale, "The reopened player applies the new size")
        assertEquals("#FFFF00", playback.preferences.subtitle_text_color)
        controller.closePlayer()
    }

    @Test fun leavingSettingsForAnotherPageEndsPlayback() = runTest {
        val server = Server()
        val controller = controller(server)
        play(controller)
        controller.openPlayerSettings(); runCurrent()
        controller.navigate(Screen.Library); runCurrent()
        val state = controller.state.value
        assertNull(state.playback)
        assertFalse(state.settingsReturnsToPlayer)
        assertEquals(listOf("lease-1"), server.released, "Leaving for another page releases the lease")
        controller.navigate(Screen.Settings); runCurrent()
        controller.navigateBack(); runCurrent()
        assertEquals(Screen.Library, controller.state.value.screen)
    }

    @Test fun aLeaseReacquiredDuringTheVisitStaysBehindSettings() = runTest {
        val server = Server()
        val controller = controller(server)
        play(controller)
        controller.openPlayerSettings(); runCurrent()
        controller.suspendPlayback(); controller.appForeground(false)
        advanceTimeBy(5 * 60_000); runCurrent()
        server.heartbeat = { lease -> if (lease == "lease-1") HttpStatusCode.Gone else HttpStatusCode.OK }
        controller.resumePlayback(); controller.appForeground(true); runCurrent()
        assertEquals(2, server.opens.size)
        assertEquals(754.0, server.opens[1].getValue("position").jsonPrimitive.double)
        assertEquals(Screen.Settings, controller.state.value.screen, "Re-acquiring the lease must not leave Settings")
        assertTrue(controller.state.value.paused)
        controller.navigateBack(); runCurrent()
        val state = controller.state.value
        assertEquals(Screen.Player, state.screen)
        assertEquals("lease-2", state.playback?.id)
        assertEquals(754_000L, state.playback?.positionMillis)
        assertTrue(state.paused)
        assertNull(state.error)
        controller.closePlayer()
    }

    @Test fun aGatewayStreamReturnsAtTheSameTitlePositionWithoutReopening() = runTest {
        val server = Server().apply { gateway = true; offsetSeconds = 600.0 }
        val controller = controller(server)
        play(controller, positionMillis = 801_500)
        val input = assertNotNull(controller.state.value.playback)
        assertEquals(600_000L, input.timelineOffsetMillis)
        controller.openPlayerSettings(); runCurrent()
        val current = assertNotNull(controller.state.value.preferences).preferences
        controller.updatePreferences(current.copy(subtitle_font_scale = 1.25)); runCurrent()
        controller.navigateBack(); runCurrent()
        assertSame(input, controller.state.value.playback, "Size and colours are drawn by this device: no new lease")
        assertEquals(1, server.opens.size)
        assertEquals(201_500L, input.positionMillis)
        assertEquals(1.25, input.preferences.subtitle_font_scale)
        controller.closePlayer()
    }

    @Test fun aLanguageChangedForAGatewayStreamReopensItAtTheSamePositionPaused() = runTest {
        val server = Server().apply { gateway = true; offsetSeconds = 600.0 }
        val controller = controller(server)
        play(controller, positionMillis = 801_500)
        controller.openPlayerSettings(); runCurrent()
        val current = assertNotNull(controller.state.value.preferences).preferences
        controller.updatePreferences(current.copy(preferred_audio_language = "ja")); runCurrent()
        controller.navigateBack(); runCurrent()
        assertEquals(2, server.opens.size)
        assertEquals(801.5, server.opens[1].getValue("position").jsonPrimitive.double)
        val state = controller.state.value
        assertEquals(Screen.Player, state.screen)
        assertEquals("lease-2", state.playback?.id)
        assertEquals("ja", state.playback?.preferences?.preferred_audio_language)
        assertTrue(state.paused)
        assertNull(state.error)
        assertEquals(listOf("lease-1"), server.released)
        controller.closePlayer()
    }

    @Test fun aTrackPickedInThePlayerDoesNotReopenAGatewayStreamOnReturn() = runTest {
        val server = Server().apply { gateway = true }
        val controller = controller(server)
        play(controller)
        controller.rememberTrackChoice(org.viptv.app.player.PlaybackTrackKind.Audio, "ja"); runCurrent()
        assertEquals(org.viptv.app.player.canonicalLanguageTag("ja"), server.preferences.preferences.preferred_audio_language)
        controller.openPlayerSettings(); runCurrent()
        controller.navigateBack(); runCurrent()
        assertEquals(1, server.opens.size)
        assertEquals("lease-1", controller.state.value.playback?.id)
        controller.closePlayer()
    }

    @Test fun theLinkDoesNothingWithoutAPlayer() = runTest {
        val server = Server()
        val controller = controller(server)
        controller.openPlayerSettings(); runCurrent()
        assertEquals(Screen.Sources, controller.state.value.screen)
        assertFalse(controller.state.value.settingsReturnsToPlayer)
    }
}
