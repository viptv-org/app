package org.viptv.app.state

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Test
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteFailure
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import org.viptv.app.downloads.DownloadBook
import org.viptv.app.downloads.DownloadEngine
import org.viptv.app.downloads.DownloadNetwork
import org.viptv.app.downloads.DownloadRecord
import org.viptv.app.downloads.DownloadState
import org.viptv.app.downloads.MemoryFiles
import org.viptv.app.downloads.downloadId
import org.viptv.app.features.downloads.OFFLINE_BANNER
import org.viptv.app.startupUnreachable
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.EpisodeCoordinates
import org.viptv.engine.wire.MediaIdentity
import java.io.IOException
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineStartupTest {
    private enum class Server { Up, Down, Hanging, Expired }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val network = MutableStateFlow(DownloadNetwork.Unmetered)
    private val requests = Collections.synchronizedList(mutableListOf<String>())
    @Volatile private var server = Server.Up
    private val store = object : RemoteSessionStore {
        override suspend fun read() = SavedRemoteSession("https://watch.example", "r1")
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val series = MediaIdentity("tt1", "series", emptyList())
    private val episodes = (1..2).map { n ->
        Card(MediaIdentity("tt1:1:$n", "series", emptyList(), episode = EpisodeCoordinates("aired", 1, n.toLong())),
            provider_id = "catalog", title = "Episode $n", badges = emptyList(), parent_identity = series)
    }
    private val client get() = HttpClient(MockEngine { request ->
        val path = request.url.encodedPath
        requests += path
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        when (server) {
            Server.Down -> throw IOException("connection refused")
            Server.Hanging -> { delay(1_000); throw IOException("timed out") }
            Server.Expired -> if (path == "/api/auth/device/refresh") throw RemoteFailure(401, "Sign in to continue.", sessionExpired = true)
                else respond("{}", HttpStatusCode.OK, json)
            Server.Up -> when (path) {
                "/api/auth/device/refresh" -> respond("""{"access_token":"a1","refresh_token":"r1"}""", HttpStatusCode.OK, json)
                "/api/bootstrap" -> respond("""{"identity":{"profiles":[{"id":"12","name":"Me"}],"profile_id":"12"},"home":null}""", HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.OK, json)
            }
        }
    }) { configureTransport() }

    @After fun stop() = scope.cancel()

    @Test fun anUnreachableServerOpensDownloadsOfflineAndRestoresInPlaceWhenItAnswers() = runBlocking<Unit> {
        server = Server.Down
        val app = controller()
        app.restore()
        val offline = withTimeout(5_000) { app.state.first { it.screen == Screen.Downloads && !it.starting } }
        assertTrue(offline.offline, "the offline banner shows")
        assertNull(offline.error, "a connectivity failure never reads as signed out")
        assertNull(app.connection)
        assertFalse(app.downloadsOnline())

        server = Server.Up
        val restored = withTimeout(5_000) { app.state.first { it.selectedProfile == "12" && !it.offline } }
        assertEquals(Screen.Downloads, restored.screen, "the restore keeps the screen")
        assertTrue(app.downloadsOnline())
        assertTrue(requests.none { it.contains("login") }, "no sign-in is needed")
    }

    @Test fun aServerThatNeverAnswersOpensDownloadsWithinTheStartLimit() = runBlocking<Unit> {
        server = Server.Hanging
        val app = controller(offlineStartMillis = 300)
        val started = System.nanoTime()
        app.restore()
        withTimeout(5_000) { app.state.first { it.screen == Screen.Downloads && !it.starting && it.offline } }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000, "start-up never waits for the request timeout")
        assertNull(app.connection, "the stalled restore is released")
    }

    @Test fun anExpiredSessionStillAsksForASignIn() = runBlocking<Unit> {
        server = Server.Expired
        val app = controller()
        app.restore()
        val state = withTimeout(5_000) { app.state.first { it.error != null } }
        assertEquals(Screen.Connect, state.screen)
        assertEquals("Session expired. Sign in again.", state.error)
        assertFalse(state.startupRetryable)
    }

    @Test fun theRestoreWaitsUntilLocalPlaybackEnds() = runBlocking<Unit> {
        server = Server.Down
        val app = controller()
        app.restore()
        withTimeout(5_000) { app.state.first { it.screen == Screen.Downloads && !it.starting } }
        app.playDownload(downloadId("12", episodes[0]))
        withTimeout(5_000) { app.state.first { it.playback != null } }

        server = Server.Up
        app.retryStartup()
        withTimeout(5_000) { while (requests.count { it == "/api/bootstrap" } == 0) delay(10) }
        delay(200)
        assertEquals(Screen.Player, app.state.value.screen, "a reachable server never interrupts local playback")
        assertNull(app.connection)

        app.closePlayer()
        val restored = withTimeout(5_000) { app.state.first { it.selectedProfile == "12" } }
        assertEquals(Screen.Downloads, restored.screen)
    }

    @Test fun withoutDownloadsAStalledSessionKeepsTheStartupScreenAndRetriesOnItsOwn() = runBlocking<Unit> {
        server = Server.Down
        val app = ApplicationController(scope, store, startupRetryDelays = emptyList(),
            remoteFactory = { origin, storage -> RemoteConnection(origin, storage, client, browserMode = false) },
            reconnectDelays = listOf(50L), downloads = DownloadEngine(MemoryFiles(), client, network, scope))
        app.restore()
        withTimeout(5_000) { while (requests.count { it == "/api/auth/device/refresh" } < 3) delay(10) }
        repeat(20) {
            val state = app.state.value
            assertTrue(startupUnreachable(state, browser = false) || state.starting, "never the sign-in screen: $state")
            delay(10)
        }
        server = Server.Up
        val restored = withTimeout(5_000) { app.state.first { it.screen == Screen.Home } }
        assertEquals("12", restored.selectedProfile)
        assertTrue(requests.none { it.contains("login") })
    }

    @Test fun anExpiredSessionWithoutDownloadsIsNotRetriedOnItsOwn() = runBlocking<Unit> {
        server = Server.Expired
        val app = ApplicationController(scope, store, remoteFactory = { origin, storage -> RemoteConnection(origin, storage, client, browserMode = false) },
            reconnectDelays = listOf(20L), downloads = DownloadEngine(MemoryFiles(), client, network, scope))
        app.restore()
        try { withTimeout(5_000) { app.state.first { it.error != null } } }
        catch (error: kotlinx.coroutines.TimeoutCancellationException) { throw AssertionError("${app.state.value} $requests", error) }
        delay(200)
        assertEquals(1, requests.count { it == "/api/auth/device/refresh" })
        assertFalse(startupUnreachable(app.state.value, browser = false))
    }

    @Test fun offlineAtRuntimePlaysDownloadsLocallyAndExplainsTheRest() = runBlocking<Unit> {
        val app = controller()
        app.connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
        app.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "12")
        server = Server.Down
        app.backendOutcome(false)
        withTimeout(5_000) { app.state.first { it.offline } }
        assertFalse(app.downloadsOnline())

        requests.clear()
        app.playTitle(episodes[1])
        assertEquals(OFFLINE_BANNER, app.state.value.notice?.text, "a title that isn't downloaded explains itself")
        assertNull(app.state.value.pendingAction, "no spinner waits for the server")

        app.playTitle(episodes[0])
        val playing = withTimeout(5_000) { app.state.first { it.playback != null } }
        assertTrue(playing.playback!!.uri.startsWith("file://"))
        delay(200)
        assertTrue(requests.none { it.startsWith("/api/profiles") || it == "/api/segments" || it == "/api/playback" },
            "a downloaded title plays without server calls: $requests")

        server = Server.Up
        app.connection!!.probeHealth(1_000)
        withTimeout(5_000) { app.state.first { !it.offline } }
        assertTrue(app.downloadsOnline())
    }

    private fun controller(offlineStartMillis: Long = 4_000) = ApplicationController(scope, store,
        remoteFactory = { origin, storage -> RemoteConnection(origin, storage, client, browserMode = false) },
        offlineStartMillis = offlineStartMillis, reconnectDelays = listOf(50L), downloads = engine())

    private fun engine(): DownloadEngine {
        val files = MemoryFiles()
        val record = DownloadRecord(downloadId("12", episodes[0]), "12", episodes[0], "Show", DownloadState.Completed,
            bytesDone = 10, bytesTotal = 10, url = "https://cdn.test/1", addedAtMillis = 1)
        files.write(record.fileName, ByteArray(10))
        files.writeBook(Json.encodeToString(DownloadBook.serializer(), DownloadBook(listOf(record))))
        return DownloadEngine(files, client, network, scope, retryDelays = listOf(10, 10, 10), saveDelayMillis = 10)
    }
}
