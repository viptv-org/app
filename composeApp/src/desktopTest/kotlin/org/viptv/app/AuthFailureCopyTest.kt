package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class AuthFailureCopyTest {
    private class Store : RemoteSessionStore {
        var saved: SavedRemoteSession? = null
        var clears = 0
        override suspend fun read() = saved
        override suspend fun save(session: SavedRemoteSession) { saved = session }
        override suspend fun clear() { clears++; saved = null }
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val bootstrapBody = """{"identity":{"profiles":[],"profile_id":null},"home":null}"""
    private val deviceSession = """{"access_token":"a1","refresh_token":"r1"}"""
    private val unauthorized = """{"error":"Unauthorized","error_code":"unauthorized"}"""
    private val credentialsCopy = "The username or password is not correct."
    private val expiredCopy = "Session expired. Sign in again."

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(body, status, jsonHeaders)

    private fun client(handle: suspend MockRequestHandleScope.(String) -> HttpResponseData) =
        HttpClient(MockEngine { request -> handle(request.url.encodedPath) }) { configureTransport() }

    private fun CoroutineScope.signInController(browser: Boolean, store: RemoteSessionStore, server: HttpClient) = ApplicationController(this, store,
        remoteFactory = { origin, storage -> RemoteConnection(origin, storage, server, browserMode = browser) })

    @Test fun theWebSignInFormSaysTheCredentialsAreWrong() = runBlocking {
        val store = Store()
        val server = client { path ->
            assertEquals("/api/auth/login", path)
            json(unauthorized, HttpStatusCode.Unauthorized)
        }
        val controller = signInController(browser = true, store, server)
        try {
            controller.connect("https://watch.example", "viptv-qa", "definitely-wrong-pw")
            requireNotNull(controller.job).join()
            val state = controller.state.value
            assertEquals(Screen.Connect, state.screen)
            assertEquals(credentialsCopy, state.error)
            assertFalse(state.busy)
            assertNull(controller.connection, "A refused sign-in leaves no connection behind")
            assertEquals(0, store.clears, "A refused sign-in clears no stored session")
        } finally { controller.disconnect(); server.close() }
    }

    @Test fun theDeviceSignInFormSaysTheCredentialsAreWrong() = runBlocking {
        val store = Store()
        val server = client { path ->
            assertEquals("/api/auth/device/login", path)
            json(unauthorized, HttpStatusCode.Unauthorized)
        }
        val controller = signInController(browser = false, store, server)
        try {
            controller.connect("https://watch.example", "viptv-qa", "definitely-wrong-pw")
            requireNotNull(controller.job).join()
            assertEquals(Screen.Connect, controller.state.value.screen)
            assertEquals(credentialsCopy, controller.state.value.error)
            assertNull(controller.connection)
        } finally { controller.disconnect(); server.close() }
    }

    @Test fun aNetworkFailureLeavesTheSignedInSessionAlone() = runBlocking {
        val store = Store()
        var offline = false
        val server = client { path ->
            if (offline) throw java.io.IOException("network down")
            when (path) {
                "/api/auth/device/login" -> json(deviceSession)
                "/api/bootstrap" -> json(bootstrapBody)
                else -> error("Unexpected $path")
            }
        }
        val remote = RemoteConnection("https://watch.example", store, server, browserMode = false)
        remote.login("member", "password")
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "12")
        try {
            offline = true
            controller.search("dune")
            requireNotNull(controller.job).join()
            val state = controller.state.value
            assertEquals(Screen.Search, state.screen)
            assertEquals("Could not reach your server. Check the connection and try again.", state.error)
            assertSame(remote, controller.connection, "A request that never reached the server cannot end the session")
            assertEquals("r1", store.saved?.refreshToken)
            assertEquals(0, store.clears)
        } finally { controller.disconnect(); server.close() }
    }

    @Test fun anUnauthorizedReplyThatIsNotASessionExpiryLeavesTheSessionAlone() = runBlocking {
        val store = Store()
        val controller = ApplicationController(this, store)
        val remote = object : RemoteConnection("https://watch.example", store,
            HttpClient(MockEngine { respond("{}", HttpStatusCode.Unauthorized, jsonHeaders) }) { configureTransport() },
            browserMode = false) {
            override suspend fun searchEventStream(path: String, onEvent: suspend (String?, String?) -> Unit) =
                throw RemoteFailure(401, "Sign in to continue.")
        }
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "12")
        try {
            controller.search("dune")
            requireNotNull(controller.job).join()
            assertEquals(Screen.Search, controller.state.value.screen)
            assertEquals("Sign in to continue.", controller.state.value.error)
            assertSame(remote, controller.connection)
            assertEquals(0, store.clears)
        } finally { controller.disconnect(); remote.close() }
    }

    @Test fun aRefusedStreamHandshakeRotatesTheTokenOnceAndReplays() = runBlocking {
        val store = Store()
        var refreshes = 0
        val server = client { path ->
            when (path) {
                "/api/auth/device/login" -> json(deviceSession)
                "/api/bootstrap" -> json(bootstrapBody)
                "/api/auth/device/refresh" -> {
                    refreshes++
                    json("""{"access_token":"a2","refresh_token":"r2"}""")
                }
                else -> error("Unexpected $path")
            }
        }
        val remote = RemoteConnection("https://watch.example", store, server, browserMode = false)
        try {
            remote.login("member", "password")
            var attempts = 0
            remote.streamWithRetry { attempts++; attempts == 1 }
            assertEquals(2, attempts, "A refused handshake is replayed once")
            assertEquals(1, refreshes)
            assertEquals("r2", store.saved?.refreshToken)
        } finally { remote.close() }
    }

    @Test fun aStreamStillRefusedAfterARefreshIsAFailedRequestNotAnExpiry() = runBlocking {
        val store = Store()
        var refreshes = 0
        val server = client { path ->
            when (path) {
                "/api/auth/device/login" -> json(deviceSession)
                "/api/bootstrap" -> json(bootstrapBody)
                "/api/auth/device/refresh" -> {
                    refreshes++
                    json("""{"access_token":"a2","refresh_token":"r2"}""")
                }
                else -> json("{}", HttpStatusCode.Unauthorized)
            }
        }
        val remote = RemoteConnection("https://watch.example", store, server, browserMode = false)
        try {
            remote.login("member", "password")
            val failure = assertFailsWith<RemoteFailure> { remote.streamWithRetry { true } }
            assertEquals(401, failure.status)
            assertFalse(failure.sessionExpired, "Only the refresh endpoint ends a session")
            assertEquals(1, refreshes)
            assertEquals("r2", store.saved?.refreshToken)
        } finally { remote.close() }
    }

    @Test fun aStreamHandshakeAfterARejectedRefreshEndsTheSession() = runBlocking {
        val store = Store()
        val server = client { path ->
            when (path) {
                "/api/auth/device/login" -> json(deviceSession)
                "/api/bootstrap" -> json(bootstrapBody)
                else -> json(unauthorized, HttpStatusCode.Unauthorized)
            }
        }
        val remote = RemoteConnection("https://watch.example", store, server, browserMode = false)
        try {
            remote.login("member", "password")
            val failure = assertFailsWith<RemoteFailure> { remote.streamWithRetry { true } }
            assertTrue(failure.sessionExpired)
            assertNull(store.saved)
        } finally { remote.close() }
    }

    @Test fun aRejectedRefreshEndsTheSession() = runBlocking {
        val store = Store()
        var expired = false
        val server = client { path ->
            when {
                path == "/api/auth/device/login" -> json(deviceSession)
                !expired && path == "/api/bootstrap" -> json(bootstrapBody)
                path == "/api/auth/device/refresh" -> json(unauthorized, HttpStatusCode.Unauthorized)
                else -> json("{}", HttpStatusCode.Unauthorized)
            }
        }
        val remote = RemoteConnection("https://watch.example", store, server, browserMode = false)
        remote.login("member", "password")
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "12")
        try {
            expired = true
            controller.search("dune")
            requireNotNull(controller.job).join()
            assertEquals(Screen.Connect, controller.state.value.screen)
            assertEquals(expiredCopy, controller.state.value.error)
            assertNull(controller.connection)
            assertNull(store.saved)
        } finally { controller.disconnect(); server.close() }
    }
}