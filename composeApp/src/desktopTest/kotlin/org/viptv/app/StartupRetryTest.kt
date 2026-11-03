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

    private class Fixture(scope: CoroutineScope, private val session: SavedRemoteSession) {
        val requests = mutableListOf<String>()
        var failWith: (Int) -> Unit = {}
        var unavailableFirst = false
        var gate: CompletableDeferred<Unit>? = null
        val body = """{"identity":{"profiles":[{"id":"p1","name":"Me"}],"profile_id":"p1"},"home":null}"""
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = session
            override suspend fun savedConnection() = SavedConnection.Remote(session)
            override suspend fun save(value: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val controller: ApplicationController

        init {
            controller = ApplicationController(scope, store, remoteFactory = { origin, storage ->
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
                    if (unavailableFirst && attempts == 1) respond("{}", HttpStatusCode.ServiceUnavailable,
                        headersOf(HttpHeaders.ContentType, "application/json"))
                    else {
                        failWith(attempts)
                        gate?.await()
                        respond("""{"access_token":"a1","refresh_token":"r1"}""", HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"))
                    }
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
        val cases = listOf("transport" to "Could not reach your server", "unavailable" to "The server could not complete this request.")
        for ((failure, copy) in cases) {
            val fixture = Fixture(this, saved)
            if (failure == "transport") fixture.failWith = { attempt -> if (attempt == 1) throw IOException("no route to host") }
            else fixture.unavailableFirst = true
            try {
                fixture.launch()
                val settled = fixture.controller.state.value
                assertEquals(Screen.Connect, settled.screen, "$failure keeps the viewer on Connect")
                assertFalse(settled.starting, "$failure settles the loading state")
                assertFalse(settled.busy, "$failure settles the busy state")
                assertContains(settled.error.orEmpty(), copy, message = "$failure explains itself")
                assertTrue(settled.startupRetryable, "$failure can be retried")
                assertNull(fixture.controller.connection, "$failure releases the failed connection")
                assertEquals(1, fixture.refreshes())

                fixture.controller.retryStartup()
                fixture.controller.job?.join()
                val restored = fixture.controller.state.value
                assertEquals(Screen.Home, restored.screen, "$failure reaches Home on retry")
                assertEquals("p1", restored.selectedProfile)
                assertNull(restored.error)
                assertFalse(restored.startupRetryable, "A successful retry retires the action")
                assertEquals(2, fixture.refreshes(), "The retry validates the saved session again")
                assertEquals(1, fixture.bootstraps())
                assertEquals(0, fixture.logins(), "No sign-in is needed")
            } finally { fixture.controller.disconnect() }
        }
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
