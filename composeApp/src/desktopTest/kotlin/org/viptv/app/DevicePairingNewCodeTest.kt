package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

/**
 * app#129: "New code" asks the server for a fresh code and sends the `device_code` this screen
 * still holds, so the server expires the superseded one (backend#42). The first request of a
 * pairing sends none, a code issued by one server is never offered to another, and a redeemed
 * code is not offered again.
 */
class DevicePairingNewCodeTest {
    private class FakeServer(private val approved: Boolean = false) {
        val bodies = java.util.Collections.synchronizedList(mutableListOf<JsonObject>())
        fun connection(origin: String, store: RemoteSessionStore) = RemoteConnection(origin, store, HttpClient(MockEngine { request ->
            val (body, status) = when (request.url.encodedPath) {
                "/api/auth/device/code" -> {
                    bodies += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    val code = "ABCD-%04d".format(bodies.size)
                    """{"device_code":"secret-$code","user_code":"$code","verification_uri":"https://watch.example/device","verification_uri_complete":"https://watch.example/device?code=$code","expires_in":600,"interval":1}""" to HttpStatusCode.OK
                }
                "/api/auth/device/token" ->
                    if (approved) """{"access_token":"approved-access","refresh_token":"approved-refresh"}""" to HttpStatusCode.OK
                    else """{"error":"authorization_pending"}""" to HttpStatusCode.BadRequest
                "/api/bootstrap" -> """{"identity":{"profiles":[],"profile_id":null},"home":null}""" to HttpStatusCode.OK
                else -> error("Unexpected pairing route ${request.url.encodedPath}")
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = false)
    }

    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    @Test fun newCodeSendsThePreviousDeviceCodeAndTheFirstSendsNone() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = FakeServer()
        val controller = ApplicationController(scope, store, remoteFactory = server::connection)
        controller.mutable.value = ApplicationState(screen = Screen.Connect)
        try {
            controller.startDevicePairing("https://watch.example")
            await("the first code") { server.bodies.size == 1 && controller.state.value.devicePairing?.userCode == "ABCD-0001" }
            assertFalse(server.bodies.single().containsKey("device_code"), "the first code request sends no previous code")
            assertTrue(server.bodies.single().containsKey("device_name"))

            controller.startDevicePairing("https://watch.example")
            await("a new code") { server.bodies.size == 2 && controller.state.value.devicePairing?.userCode == "ABCD-0002" }
            assertEquals("secret-ABCD-0001", server.bodies.last().getValue("device_code").jsonPrimitive.content)

            controller.startDevicePairing("https://other.example")
            await("a code from another server") { server.bodies.size == 3 }
            assertFalse(server.bodies.last().containsKey("device_code"),
                "a code issued by one server must not be offered to another")
        } finally { controller.cancelDevicePairing(); scope.cancel() }
    }

    @Test fun anApprovedPairingDoesNotOfferItsRedeemedCodeToTheNextOne() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = FakeServer(approved = true)
        val controller = ApplicationController(scope, store, remoteFactory = server::connection)
        controller.mutable.value = ApplicationState(screen = Screen.Connect)
        try {
            controller.startDevicePairing("https://watch.example")
            await("the approved pairing") { controller.state.value.screen == Screen.Profiles }
            assertEquals(1, server.bodies.size)

            controller.mutable.value = ApplicationState(screen = Screen.Connect)
            controller.startDevicePairing("https://watch.example")
            await("the next pairing's code") { server.bodies.size == 2 && controller.state.value.devicePairing != null }
            assertTrue(server.bodies.none { it.containsKey("device_code") }, "a redeemed code is not offered again")
        } finally { controller.cancelDevicePairing(); scope.cancel() }
    }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            assertTrue(System.nanoTime() < deadline, "Timed out waiting for $what")
            Thread.sleep(20)
        }
    }
}