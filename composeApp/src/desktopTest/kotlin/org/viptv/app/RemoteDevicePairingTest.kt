package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import kotlin.test.*

class RemoteDevicePairingTest {
    private class Store : RemoteSessionStore {
        var saved: SavedRemoteSession? = null
        override suspend fun read() = saved
        override suspend fun save(session: SavedRemoteSession) { saved = session }
        override suspend fun clear() { saved = null }
    }
    private val challenge = """{"device_code":"private-device-secret","user_code":"ABCD-EFGH","verification_uri":"https://watch.example/device","verification_uri_complete":"https://watch.example/device?code=ABCD-EFGH","expires_in":600,"interval":5}"""

    @Test fun pendingAndRateLimitedPollsDoNotPersistCredentialsBeforeApproval() = runBlocking {
        val store = Store(); var polls = 0; var bootstraps = 0
        val client = HttpClient(MockEngine { request ->
            val (body, status) = when (request.url.encodedPath) {
                "/api/auth/device/code" -> challenge to HttpStatusCode.OK
                "/api/auth/device/token" -> {
                    assertNull(request.headers[HttpHeaders.Authorization]); polls++
                    when (polls) {
                        1 -> """{"error_code":"authorization_pending"}""" to HttpStatusCode.BadRequest
                        2 -> """{"error_code":"rate_limited"}""" to HttpStatusCode.TooManyRequests
                        else -> """{"access_token":"approved-access","refresh_token":"approved-refresh"}""" to HttpStatusCode.OK
                    }
                }
                "/api/bootstrap" -> {
                    bootstraps++; assertEquals("Bearer approved-access", request.headers[HttpHeaders.Authorization])
                    """{"identity":{"profiles":[],"profile_id":null},"home":null}""" to HttpStatusCode.OK
                }
                else -> error("Unexpected pairing route")
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            val pairing = remote.devicePairing(); val code = pairing.create()
            assertFalse(code.toString().contains("private-device-secret"))
            assertEquals("ABCD-EFGH", code.display.userCode)
            assertNull(pairing.poll(code)); assertNull(store.saved)
            assertNull(pairing.poll(code)); assertNull(store.saved); assertEquals(10L, code.intervalSeconds)
            assertNotNull(pairing.poll(code)); assertEquals(1, bootstraps)
            assertEquals("approved-refresh", store.saved?.refreshToken)
        } finally { remote.close() }
    }

    @Test fun cancelledPairingCannotInstallLateApprovedTokens() = runBlocking {
        val store = Store()
        val client = HttpClient(MockEngine { request ->
            respond(if (request.url.encodedPath.endsWith("/code")) challenge
                else """{"access_token":"late-access","refresh_token":"late-refresh"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            val pairing = remote.devicePairing { false }; val code = pairing.create()
            assertFailsWith<CancellationException> { pairing.poll(code) }
            assertNull(store.saved)
        } finally { remote.close() }
    }

    @Test fun leavingPairingDuringBootstrapDoesNotPersistItsSession() = runBlocking {
        val store = Store(); var active = true
        val client = HttpClient(MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/auth/device/code" -> challenge
                "/api/auth/device/token" -> """{"access_token":"approved-access","refresh_token":"approved-refresh"}"""
                "/api/bootstrap" -> {
                    assertNull(store.saved)
                    active = false
                    """{"identity":{"profiles":[],"profile_id":null},"home":null}"""
                }
                else -> error("Unexpected pairing route")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            val pairing = remote.devicePairing { active }
            assertFailsWith<CancellationException> { pairing.poll(pairing.create()) }
            assertNull(store.saved)
        } finally { remote.close() }
    }

}
