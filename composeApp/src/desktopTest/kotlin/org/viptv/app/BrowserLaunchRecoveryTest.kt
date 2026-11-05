package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedConnection
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import org.viptv.app.state.ApplicationController
import org.viptv.app.state.disconnect
import org.viptv.app.state.restore
import org.viptv.app.state.retryStartup
import org.viptv.app.state.Screen
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserLaunchRecoveryTest {
    private val origin = "https://watch.example"
    private val bootstrap = """{"identity":{"profiles":[{"id":"p1","name":"Me"}],"profile_id":"p1"},"home":null}"""

    private class Fixture(scope: CoroutineScope, private val bootstrap: String) {
        val requests = mutableListOf<String>()
        var authenticated = true
        var failWith: (Int) -> Unit = {}
        private var attempts = 0
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun savedConnection(): SavedConnection = SavedConnection.Local
            override suspend fun save(value: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val controller = ApplicationController(scope, store, startupRetryDelays = listOf(0L, 0L, 0L),
            launchOrigin = { "https://watch.example" },
            remoteFactory = { target, storage ->
                RemoteConnection(target, storage, HttpClient(MockEngine { request -> answer(request.url.encodedPath) }) { configureTransport() },
                    browserMode = true)
            })

        private suspend fun io.ktor.client.engine.mock.MockRequestHandleScope.answer(path: String): io.ktor.client.request.HttpResponseData {
            synchronized(requests) { requests += path }
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            return when (path) {
                "/api/auth/status" -> respond("""{"authenticated":$authenticated}""", HttpStatusCode.OK, json)
                "/api/auth/refresh" -> respond("""{"csrf_token":"c1"}""", HttpStatusCode.OK, json)
                "/api/bootstrap" -> {
                    attempts++
                    failWith(attempts)
                    respond(bootstrap, HttpStatusCode.OK, json)
                }
                else -> error("Unexpected $path")
            }
        }

        fun count(path: String) = synchronized(requests) { requests.count { it == path } }
        fun launches() = count("/api/bootstrap")
    }

    private suspend fun launch(fixture: Fixture) {
        fixture.controller.restore()
        fixture.controller.job?.join()
    }

    @Test fun aTransportFailureIsRetriedQuietlyAndReachesHome() = runBlocking<Unit> {
        val fixture = Fixture(this, bootstrap)
        fixture.failWith = { attempt -> if (attempt <= 2) throw IOException("the network is not up yet") }
        try {
            launch(fixture)
            val state = fixture.controller.state.value
            assertEquals(Screen.Home, state.screen, "The third attempt restores the browser session")
            assertEquals("p1", state.selectedProfile)
            assertNull(state.error, "A quiet retry never shows the reason")
            assertFalse(state.starting)
            assertFalse(state.startupRetryable)
            assertEquals(3, fixture.launches())
        } finally { fixture.controller.disconnect() }
    }

    @Test fun everyAttemptFailingShowsTheReasonAndTryAgainRestoresTheCookieSession() = runBlocking<Unit> {
        val fixture = Fixture(this, bootstrap)
        fixture.failWith = { throw IOException("no route to host") }
        try {
            launch(fixture)
            val settled = fixture.controller.state.value
            assertEquals(Screen.Connect, settled.screen)
            assertContains(settled.error.orEmpty(), "Could not reach your server")
            assertTrue(settled.startupRetryable, "Try again is offered on the web too")
            assertFalse(settled.starting)
            assertFalse(settled.busy)
            assertEquals(4, fixture.launches(), "The launch attempt and its three quiet retries")

            fixture.failWith = {}
            fixture.controller.retryStartup()
            fixture.controller.job?.join()
            val restored = fixture.controller.state.value
            assertEquals(Screen.Home, restored.screen, "Try again restores the cookie session without a sign-in")
            assertNull(restored.error)
            assertFalse(restored.startupRetryable)
            assertEquals(5, fixture.launches())
            assertEquals(0, fixture.count("/api/auth/login"), "No sign-in is needed")
        } finally { fixture.controller.disconnect() }
    }

    @Test fun aServerFailureIsRetriedQuietlyAndA401IsNot() = runBlocking<Unit> {
        val unavailable = Fixture(this, bootstrap)
        unavailable.failWith = { attempt ->
            if (attempt <= 2) throw org.viptv.app.connection.RemoteFailure(503, "The server could not complete this request.")
        }
        try {
            launch(unavailable)
            assertEquals(Screen.Home, unavailable.controller.state.value.screen, "A 5xx is retried quietly")
            assertNull(unavailable.controller.state.value.error)
            assertEquals(3, unavailable.launches())
        } finally { unavailable.controller.disconnect() }

        val refused = Fixture(this, bootstrap)
        refused.failWith = { throw org.viptv.app.connection.RemoteFailure(401, "Sign in to continue.") }
        try {
            launch(refused)
            assertEquals("Sign in to continue.", refused.controller.state.value.error)
            assertEquals(1, refused.launches(), "A refusal is not a transport failure, so it is not retried")
            assertTrue(refused.controller.state.value.startupRetryable, "The saved cookie session stays retriable")
        } finally { refused.controller.disconnect() }
    }

    @Test fun aVisitorWithoutASessionLandsOnTheFormWithNoRetry() = runBlocking<Unit> {
        val fixture = Fixture(this, bootstrap)
        fixture.authenticated = false
        try {
            launch(fixture)
            val state = fixture.controller.state.value
            assertEquals(Screen.Connect, state.screen)
            assertNull(state.error, "No session is not a failure")
            assertFalse(state.startupRetryable)
            assertEquals(1, fixture.count("/api/auth/status"), "One status check, no retry")
            assertEquals(0, fixture.launches(), "No session means no bootstrap")
        } finally { fixture.controller.disconnect() }
    }
}