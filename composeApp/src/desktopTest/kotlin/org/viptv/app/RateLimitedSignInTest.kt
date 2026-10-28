package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

/**
 * app#123: a rate-limited sign-in answers 429 with `error_code: "rate_limited"` and the
 * `Retry-After` seconds (backend#59). The sign-in screen must show that window instead of
 * the generic 429 copy, while a wrong password stays an ordinary 401.
 */
class RateLimitedSignInTest {
    private class Store : RemoteSessionStore {
        var saved: SavedRemoteSession? = null
        override suspend fun read() = saved
        override suspend fun save(session: SavedRemoteSession) { saved = session }
        override suspend fun clear() { saved = null }
    }

    private val rateLimited = """{"error":"Too many login attempts","error_code":"rate_limited"}"""

    private fun limitedClient(path: String, retryAfter: String?, body: String = rateLimited) = HttpClient(MockEngine { request ->
        assertEquals(path, request.url.encodedPath)
        respond(body, HttpStatusCode.TooManyRequests, Headers.build {
            append(HttpHeaders.ContentType, "application/json")
            retryAfter?.let { append(HttpHeaders.RetryAfter, it) }
        })
    }) { configureTransport() }

    @Test fun deviceSignInReportsTheServersRetryWindow() = runBlocking {
        val store = Store()
        val remote = RemoteConnection("https://watch.example", store,
            limitedClient("/api/auth/device/login", "42"), browserMode = false)
        try {
            val failure = assertFailsWith<RemoteFailure> { remote.login("member", "password") }
            assertEquals(429, failure.status)
            assertEquals("Too many attempts. Try again in 42 s.", failure.message)
            assertNull(store.saved, "a refused sign-in must not install tokens")
        } finally { remote.close() }
    }

    @Test fun browserSignInReportsTheServersRetryWindow() = runBlocking {
        val remote = RemoteConnection("https://watch.example", Store(),
            limitedClient("/api/auth/login", "7"), browserMode = true)
        try {
            val failure = assertFailsWith<RemoteFailure> { remote.login("member", "password") }
            assertEquals(429, failure.status)
            assertEquals("Too many attempts. Try again in 7 s.", failure.message)
        } finally { remote.close() }
    }

    @Test fun wrongPasswordKeepsItsOwnSignInFailure() = runBlocking {
        for (browser in listOf(false, true)) {
            val client = HttpClient(MockEngine { request ->
                respond("""{"error":"Invalid username or password","error_code":"unauthorized"}""",
                    HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureTransport() }
            val remote = RemoteConnection("https://watch.example", Store(), client, browserMode = browser)
            try {
                val failure = assertFailsWith<RemoteFailure> { remote.login("member", "wrong-password") }
                assertEquals(401, failure.status)
                assertEquals("Sign in to continue.", failure.message)
            } finally { remote.close() }
        }
    }

    @Test fun aRateLimitWithoutTheHeaderKeepsTheGenericCopy() = runBlocking {
        val remote = RemoteConnection("https://watch.example", Store(),
            limitedClient("/api/auth/device/login", retryAfter = null), browserMode = false)
        try {
            val failure = assertFailsWith<RemoteFailure> { remote.login("member", "password") }
            assertEquals("Too many requests. Please wait and try again.", failure.message)
        } finally { remote.close() }
    }

    /** The sign-in screen renders `ApplicationState.error`, so the retry window must survive the controller. */
    @Test fun signInScreenShowsTheRetryWindow() = runBlocking {
        val client = limitedClient("/api/auth/device/login", "42")
        val controller = ApplicationController(this, Store(), remoteFactory = { origin, storage ->
            RemoteConnection(origin, storage, client, browserMode = false)
        })
        try {
            controller.connect("https://watch.example", "member", "password")
            requireNotNull(controller.job).join()
            assertEquals("Too many attempts. Try again in 42 s.", controller.state.value.error)
            assertFalse(controller.state.value.busy)
        } finally { controller.disconnect() }
    }
}
