package org.viptv.app

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import java.io.IOException
import kotlin.test.*

/** app#557: a saved session the launch could not reach is retried without a restart and without signing in again. */
class StartupRetryTest {
    private val saved = SavedRemoteSession("https://fixture.example", "r1")

    private class Fixture(scope: CoroutineScope, private val session: SavedRemoteSession, delays: List<Long> = emptyList()) {
        val requests = mutableListOf<String>()
        var failWith: (Int) -> Unit = {}
        var gate: CompletableDeferred<Unit>? = null
        var malformed: (Int) -> Boolean = { false }
        var clears = 0
        val body = """{"identity":{"profiles":[{"id":"p1","name":"Me"}],"profile_id":"p1"},"home":null}"""
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = session
            override suspend fun savedConnection() = SavedConnection.Remote(session)
            override suspend fun save(value: SavedRemoteSession) = Unit
            override suspend fun clear() { clears++ }
        }
        val controller: ApplicationController

        init {
            controller = ApplicationController(scope, store, startupRetryDelays = delays, remoteFactory = { origin, storage ->
                // A connection closes its own client, so every restore builds its own.
                RemoteConnection(origin, storage, HttpClient(MockEngine { request -> answer(request.url.encodedPath) }) { configureTransport() },
                    browserMode = false)
            })
        }

        private var attempts = 0
        private suspend fun io.ktor.client.engine.mock.MockRequestHandleScope.answer(path: String): io.ktor.client.request.HttpResponseData {
            synchronized(requests) { requests += path }
            return when (path) {
                "/api/auth/device/refresh" -> {
                    attempts++
                    failWith(attempts)
                    gate?.await()
                    if (malformed(attempts)) respond("<html>Sign in to the network</html>", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "text/html"))
                    else respond("""{"access_token":"a1","refresh_token":"r1"}""", HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                }
                "/api/bootstrap" -> respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                else -> error("Unexpected $path")
            }
        }

        fun count(path: String) = synchronized(requests) { requests.count { it == path } }
        fun refreshes() = count("/api/auth/device/refresh")
        fun bootstraps() = count("/api/bootstrap")
        fun logins() = synchronized(requests) { requests.count { it.contains("login") } }
    }

    private object NoOwner : LifecycleOwner {
        override val lifecycle: Lifecycle get() = error("The observer never reads its owner")
    }

    private suspend fun Fixture.launch() {
        controller.restore()
        controller.job?.join()
    }

    @Test fun anUnreachableSavedSessionSettlesWithAnErrorAndStaysRetriable() = runBlocking<Unit> {
        val cases = listOf(
            { throw IOException("no route to host") } to "Could not reach your server",
            { throw RemoteFailure(503, "The server could not complete this request.") } to "The server could not complete this request.")
        for ((fail, copy) in cases) {
            val fixture = Fixture(this, saved)
            fixture.failWith = { fail() }
            try {
                fixture.launch()
                val settled = fixture.controller.state.value
                assertEquals(Screen.Connect, settled.screen, "$copy keeps the viewer on Connect")
                assertFalse(settled.starting, "$copy settles the loading state")
                assertFalse(settled.busy, "$copy settles the busy state")
                assertContains(settled.error.orEmpty(), copy, message = "$copy explains itself")
                assertTrue(settled.startupRetryable, "$copy can be retried")
                assertNull(fixture.controller.connection, "$copy releases the failed connection")
                assertEquals(1, fixture.refreshes())

                fixture.failWith = {}
                fixture.controller.retryStartup()
                fixture.controller.job?.join()
                val restored = fixture.controller.state.value
                assertEquals(Screen.Home, restored.screen, "$copy reaches Home on retry")
                assertEquals("p1", restored.selectedProfile)
                assertNull(restored.error)
                assertFalse(restored.startupRetryable, "A successful retry retires the action")
                assertEquals(2, fixture.refreshes(), "The retry validates the saved session again")
                assertEquals(1, fixture.bootstraps())
                assertEquals(0, fixture.logins(), "No sign-in is needed")
            } finally { fixture.controller.disconnect() }
        }
    }

    @Test fun twoFailedAttemptsThenSuccessReachHomeWithoutShowingAnError() = runBlocking<Unit> {
        val fixture = Fixture(this, saved, delays = listOf(0L, 0L, 0L))
        fixture.failWith = { attempt -> if (attempt <= 2) throw IOException("the network is not up yet") }
        try {
            fixture.launch()
            val state = fixture.controller.state.value
            assertEquals(Screen.Home, state.screen, "The third attempt restores the saved session")
            assertEquals("p1", state.selectedProfile)
            assertNull(state.error, "A quiet retry never shows the reason")
            assertFalse(state.starting)
            assertFalse(state.startupRetryable)
            assertEquals(3, fixture.refreshes())
            assertEquals(1, fixture.bootstraps())
            assertEquals(0, fixture.logins())
        } finally { fixture.controller.disconnect() }
    }

    @Test fun fourFailedAttemptsShowTheReasonAndOfferTryAgain() = runBlocking<Unit> {
        val fixture = Fixture(this, saved, delays = listOf(0L, 0L, 0L))
        fixture.failWith = { throw IOException("no route to host") }
        try {
            fixture.launch()
            val state = fixture.controller.state.value
            assertEquals(4, fixture.refreshes(), "The launch attempt and its three quiet retries")
            assertEquals(Screen.Connect, state.screen)
            assertContains(state.error.orEmpty(), "Could not reach your server")
            assertTrue(state.startupRetryable, "Try again is still offered")
            assertFalse(state.starting)
            assertFalse(state.busy)
            assertNull(fixture.controller.connection)
        } finally { fixture.controller.disconnect() }
    }

    @Test fun aServerFailureIsRetriedAndAnExpiredSessionIsNot() = runBlocking<Unit> {
        val unavailable = Fixture(this, saved, delays = listOf(0L, 0L, 0L))
        unavailable.failWith = { attempt -> if (attempt <= 2) throw RemoteFailure(503, "The server could not complete this request.") }
        try {
            unavailable.launch()
            assertEquals(Screen.Home, unavailable.controller.state.value.screen, "A 5xx is retried quietly")
            assertNull(unavailable.controller.state.value.error)
            assertEquals(3, unavailable.refreshes())
        } finally { unavailable.controller.disconnect() }

        val expired = Fixture(this, saved, delays = listOf(0L, 0L, 0L))
        expired.failWith = { throw RemoteFailure(401, "Sign in to continue.", sessionExpired = true) }
        try {
            expired.launch()
            assertEquals("Session expired. Sign in again.", expired.controller.state.value.error)
            assertEquals(1, expired.refreshes(), "An expired session goes straight to sign-in")
            assertFalse(expired.controller.state.value.startupRetryable)
        } finally { expired.controller.disconnect() }
    }

    @Test fun returningToTheAppRetriesTheStalledSavedSession() = runBlocking<Unit> {
        val fixture = Fixture(this, saved)
        fixture.failWith = { attempt -> if (attempt == 1) throw IOException("offline") }
        var returns = 0
        val observer: LifecycleEventObserver = fixture.controller.foregroundObserver(onReturn = { returns++ })
        try {
            fixture.launch()
            assertTrue(fixture.controller.state.value.startupRetryable)

            observer.onStateChanged(NoOwner, Lifecycle.Event.ON_START)
            fixture.controller.job?.join()
            assertEquals(1, fixture.refreshes(), "The launch start is not a return, so it retries nothing")

            observer.onStateChanged(NoOwner, Lifecycle.Event.ON_STOP)
            observer.onStateChanged(NoOwner, Lifecycle.Event.ON_START)
            fixture.controller.job?.join()
            assertEquals(Screen.Home, fixture.controller.state.value.screen, "Coming back to the app restores the saved session")
            assertEquals(2, returns, "The foreground hook still runs once per start")
            assertEquals(2, fixture.refreshes())
        } finally {
            observer.onStateChanged(NoOwner, Lifecycle.Event.ON_STOP)
            fixture.controller.disconnect()
        }
    }

    @Test fun anExpiredSessionIsNotOfferedAsARetry() = runBlocking<Unit> {
        val fixture = Fixture(this, saved)
        fixture.failWith = { throw RemoteFailure(401, "Sign in to continue.", sessionExpired = true) }
        try {
            fixture.launch()
            assertEquals("Session expired. Sign in again.", fixture.controller.state.value.error)
            assertFalse(fixture.controller.state.value.startupRetryable, "An expired session needs a sign-in, not a retry")
            fixture.controller.retryStartup()
            fixture.controller.job?.join()
            assertEquals(1, fixture.refreshes(), "A retry that was not offered dispatches nothing")
        } finally { fixture.controller.disconnect() }
    }

    @Test fun aHealthyLaunchRestoresOnceAndALaterReturnRestoresNothing() = runBlocking<Unit> {
        val fixture = Fixture(this, saved)
        val observer: LifecycleEventObserver = fixture.controller.foregroundObserver()
        try {
            fixture.launch()
            assertEquals(Screen.Home, fixture.controller.state.value.screen)
            assertFalse(fixture.controller.state.value.startupRetryable)
            observer.onStateChanged(NoOwner, Lifecycle.Event.ON_START)
            observer.onStateChanged(NoOwner, Lifecycle.Event.ON_STOP)
            observer.onStateChanged(NoOwner, Lifecycle.Event.ON_START)
            fixture.controller.job?.join()
            assertEquals(1, fixture.refreshes(), "A restored session is not restored again")
            assertEquals(Screen.Home, fixture.controller.state.value.screen)
        } finally {
            observer.onStateChanged(NoOwner, Lifecycle.Event.ON_STOP)
            fixture.controller.disconnect()
        }
    }

    @Test fun aMalformedAnswerIsRetriedQuietlyAndReachesHome() = runBlocking<Unit> {
        val fixture = Fixture(this, saved, delays = listOf(0L, 0L, 0L))
        fixture.malformed = { attempt -> attempt <= 2 }
        try {
            fixture.launch()
            val state = fixture.controller.state.value
            assertEquals(Screen.Home, state.screen, "The fourth attempt answers in JSON")
            assertEquals("p1", state.selectedProfile)
            assertNull(state.error, "A quiet retry never shows the reason")
            assertFalse(state.starting)
            assertFalse(state.startupRetryable)
            assertEquals(3, fixture.refreshes())
            assertEquals(1, fixture.bootstraps())
            assertEquals(0, fixture.clears, "The saved session is never cleared")
        } finally { fixture.controller.disconnect() }
    }

    @Test fun aMalformedAnswerSettlesWithTheReasonAndTryAgainRestoresTheSession() = runBlocking<Unit> {
        val fixture = Fixture(this, saved, delays = listOf(0L, 0L, 0L))
        fixture.malformed = { true }
        try {
            fixture.launch()
            val settled = fixture.controller.state.value
            assertEquals(Screen.Connect, settled.screen, "A captive portal lands on the entry screen")
            assertEquals("The server returned an incompatible response.", settled.error)
            assertTrue(settled.startupRetryable, "Try again is offered")
            assertFalse(settled.starting)
            assertFalse(settled.busy)
            assertEquals(4, fixture.refreshes(), "The launch attempt and its three quiet retries")
            assertEquals(0, fixture.clears, "The saved session stays")

            fixture.malformed = { false }
            fixture.controller.retryStartup()
            fixture.controller.job?.join()
            val restored = fixture.controller.state.value
            assertEquals(Screen.Home, restored.screen, "Try again restores without a sign-in")
            assertNull(restored.error)
            assertFalse(restored.startupRetryable, "A successful retry retires the action")
            assertEquals(5, fixture.refreshes())
            assertEquals(1, fixture.bootstraps())
            assertEquals(0, fixture.logins(), "No sign-in is needed")
            assertEquals(0, fixture.clears)
        } finally { fixture.controller.disconnect() }
    }

    @Test fun repeatedPressesWhileTheRetryRunsDispatchOneRestore() = runBlocking<Unit> {
        val fixture = Fixture(this, saved)
        fixture.failWith = { attempt -> if (attempt == 1) throw IOException("offline") }
        val released = CompletableDeferred<Unit>()
        try {
            fixture.launch()
            fixture.gate = released
            fixture.controller.retryStartup()
            val retrying = fixture.controller.job
            withTimeout(2_000) { while (fixture.refreshes() < 2) delay(10) }
            assertTrue(fixture.controller.state.value.starting, "A retry shows the loading state again")
            repeat(3) { fixture.controller.retryStartup() }
            released.complete(Unit)
            retrying?.join()
            assertEquals(2, fixture.refreshes(), "Every press during a running retry is ignored")
            assertEquals(Screen.Home, fixture.controller.state.value.screen)
        } finally {
            released.complete(Unit)
            fixture.controller.disconnect()
        }
    }
}
