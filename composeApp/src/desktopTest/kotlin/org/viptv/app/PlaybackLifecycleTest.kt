package org.viptv.app

import kotlinx.coroutines.runBlocking
import org.viptv.app.player.PlaybackInput
import org.viptv.app.player.PlayerFact
import org.viptv.app.connection.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import org.viptv.app.state.*
import kotlin.test.*

class PlaybackLifecycleTest {
    @Test fun leavingAndReturningKeepsPlaybackAndRouteWithoutResumingUserPause() = runBlocking {
        val controller = ApplicationController(this)
        val input = PlaybackInput("attempt", "https://media.example/movie.mp4", positionMillis = 50_000)
        controller.mutable.value = ApplicationState(screen = Screen.Player, playback = input)
        controller.suspendPlayback()
        controller.suspendPlayback()
        assertSame(input, controller.state.value.playback)
        assertEquals(Screen.Player, controller.state.value.screen)
        assertTrue(controller.state.value.paused)
        controller.resumePlayback()
        assertFalse(controller.state.value.paused)
        controller.pausePlayback()
        controller.suspendPlayback(); controller.resumePlayback()
        assertTrue(controller.state.value.paused)
    }

    @Test fun backgroundResumeCannotRestartAnotherAttemptOrCompletedPlayback() = runBlocking {
        val controller = ApplicationController(this)
        val first = PlaybackInput("first", "https://media.example/first.mp4")
        val next = PlaybackInput("next", "https://media.example/next.mp4")
        controller.mutable.value = ApplicationState(screen = Screen.Player, playback = first)
        controller.suspendPlayback()
        controller.mutable.value = controller.state.value.copy(playback = next)
        controller.resumePlayback()
        assertTrue(controller.state.value.paused)
        assertSame(next, controller.state.value.playback)
        controller.mutable.value = controller.state.value.copy(paused = false)
        controller.suspendPlayback(); controller.completionSeen = true; controller.resumePlayback()
        assertTrue(controller.state.value.paused)
    }

    @Test fun rendererStopSampleIsSavedEvenImmediatelyAfterThePeriodicSave() = runBlocking {
        val positions = mutableListOf<Long>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/profiles/profile/progress", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            positions += body.getValue("record").jsonObject.getValue("position_millis").jsonPrimitive.long
            respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", client = client, browserMode = false)
        val controller = ApplicationController(this)
        val input = PlaybackInput("attempt", "https://media.example/movie.mp4", profileId = "profile")
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Player, playback = input,
            sourceTitle = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "metadata", title = "Movie", badges = emptyList()))
        controller.lastProgress = PlayerFact.Progress(50_000, 100_000)
        try {
            controller.suspendPlayback(); controller.progressJob?.join()
            controller.observePlaybackFor(input, PlayerFact.Progress(50_199, 100_000))
            controller.progressJob?.join()
            assertEquals(listOf(50_000L, 50_199L), positions)
            controller.observePlaybackFor(input, PlayerFact.Progress(50_199, 100_000))
            controller.progressJob?.join()
            assertEquals(2, positions.size, "Repeated paused polling must not resend progress")
        } finally { remote.close() }
    }

    @Test fun leavingWhileAlreadyPausedSavesThePositionWithoutResumingOnReturn() = runBlocking {
        val positions = mutableListOf<Long>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/profiles/profile/progress", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            positions += body.getValue("record").jsonObject.getValue("position_millis").jsonPrimitive.long
            respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", client = client, browserMode = false)
        val controller = ApplicationController(this)
        val input = PlaybackInput("attempt", "https://media.example/movie.mp4", profileId = "profile")
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Player, playback = input, paused = true,
            sourceTitle = Card(MediaIdentity("movie", "movie", emptyList()), provider_id = "metadata", title = "Movie", badges = emptyList()))
        controller.lastProgress = PlayerFact.Progress(28_957_000, 44_000_000)
        try {
            controller.suspendPlayback(); controller.progressJob?.join()
            assertEquals(listOf(28_957_000L), positions)
            controller.resumePlayback()
            assertTrue(controller.state.value.paused)
        } finally { remote.close() }
    }
}
