package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
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

    @Test fun deniedAndExpiredCodesEndPollingWithTheirOwnMessages() = runBlocking {
        val answers = ArrayDeque(listOf(
            """{"error":"authorization_pending"}""", """{"error":"access_denied"}""",
            """{"error":"Access denied","error_code":"access_denied"}""", """{"error":"expired_token"}"""))
        val client = HttpClient(MockEngine { request ->
            val body = if (request.url.encodedPath.endsWith("/code")) challenge else answers.removeFirst()
            respond(body, if (body == challenge) HttpStatusCode.OK else HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", Store(), client, browserMode = false)
        try {
            val pairing = remote.devicePairing(); val code = pairing.create()
            assertNull(pairing.poll(code), "authorization_pending is never a denial")
            repeat(2) {
                val denied = assertFailsWith<DevicePairingEnded> { pairing.poll(code) }
                assertEquals(DevicePairingEnded.Reason.Denied, denied.reason)
                assertEquals("Sign-in was declined. Request a new code.", denied.message)
            }
            val expired = assertFailsWith<DevicePairingEnded> { pairing.poll(code) }
            assertEquals(DevicePairingEnded.Reason.Expired, expired.reason)
            assertEquals("This sign-in code expired. Request a new code.", expired.message)
        } finally { remote.close() }
    }

    @Test fun aNewCodeSendsThePreviousDeviceCodeAndTheFirstCodeSendsNone() = runBlocking {
        val bodies = mutableListOf<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            bodies += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond(challenge, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", Store(), client, browserMode = false)
        try {
            val pairing = remote.devicePairing()
            val first = pairing.create()
            assertFalse(bodies.single().containsKey("device_code"), "the first code request sends no previous code")
            assertTrue(bodies.single().containsKey("device_name"))

            pairing.create(first.secret)
            assertEquals("private-device-secret", bodies.last().getValue("device_code").jsonPrimitive.content)
            assertEquals(setOf("device_name", "device_code"), bodies.last().keys)

            pairing.create("  ")
            assertFalse(bodies.last().containsKey("device_code"), "a blank previous code is not sent")
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
