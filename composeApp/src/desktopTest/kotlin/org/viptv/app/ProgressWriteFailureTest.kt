package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.player.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ProgressWriteFailureTest {
    private class Store : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private val card = Card(identity = MediaIdentity("opaque-movie", "movie", emptyList()), provider_id = "provider", title = "Movie", badges = emptyList())

    private fun controller(client: HttpClient, scope: CoroutineScope): ApplicationController {
        val controller = ApplicationController(scope, Store(), progressRetryDelays = listOf(200L, 400L))
        controller.connection = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        return controller
    }

    private fun ApplicationController.playing(): PlaybackInput {
        val input = PlaybackInput("lease", "https://media.example/movie", profileId = "12")
        mutable.value = ApplicationState(screen = Screen.Player, selectedProfile = "12", sourceTitle = card, playback = input)
        observePlayback(PlayerFact.Progress(12_000, 60_000))
        return input
    }

    @Test fun failedPositionWriteLeavesThePlayerRunning() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/profiles/12/progress", request.url.encodedPath)
            respondError(HttpStatusCode.ServiceUnavailable)
        }) { configureTransport() }
        val controller = controller(client, this)
        val input = controller.playing()
        controller.saveProgress(false)?.join()
        controller.progressRetryJob?.cancel()
        val state = controller.state.value
        assertNull(state.error)
        assertSame(input, state.playback)
        assertEquals(Screen.Player, state.screen)
        assertFalse(state.paused)
    }

    @Test fun failedPositionWriteIsRetriedWithTheLatestPosition() = runBlocking {
        val calls = AtomicInteger()
        val sent = CompletableDeferred<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/profiles/12/progress", request.url.encodedPath)
            if (calls.incrementAndGet() == 1) respondError(HttpStatusCode.ServiceUnavailable)
            else {
                sent.complete(Json.parseToJsonElement((request.body as TextContent).text).jsonObject)
                respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }) { configureTransport() }
        val controller = controller(client, this)
        val input = controller.playing()
        controller.saveProgress(false)?.join()
        controller.observePlayback(PlayerFact.Progress(42_000, 60_000))
        val body = withTimeout(5_000) { sent.await() }
        controller.progressRetryJob?.cancel()
        val record = Json.decodeFromJsonElement<PlaybackRecord>(body.getValue("record"))
        assertEquals(42_000L, record.position_millis)
        assertEquals(false, body.getValue("ended").jsonPrimitive.boolean)
        val state = controller.state.value
        assertNull(state.error)
        assertSame(input, state.playback)
        assertEquals(Screen.Player, state.screen)
    }

    @Test fun streamFailureStillReportsTheSource() = runBlocking {
        val client = HttpClient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }) { configureTransport() }
        val controller = controller(client, this)
        val input = controller.playing()
        controller.observePlayback(PlayerFact.Failed)
        controller.progressRetryJob?.cancel()
        val state = controller.state.value
        assertNotNull(state.error)
        assertSame(input, state.playback)
    }
}