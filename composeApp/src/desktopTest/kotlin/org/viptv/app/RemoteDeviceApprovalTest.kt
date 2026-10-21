package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import kotlin.test.*

class RemoteDeviceApprovalTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val pending = """{"user_code":"12345678","device":{"device_name":"Living room TV","status":"pending"},"expires_at":"1900000000"}"""

    private suspend fun signedIn(handler: suspend MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData): RemoteConnection {
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/auth/device/login" -> respond("""{"access_token":"access-1","refresh_token":"refresh-1","csrf_token":"c"}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
                "/api/bootstrap" -> respond("""{"identity":{"profiles":[],"profile_id":null},"home":null}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> handler(request)
            }
        }) { configureTransport() }
        return RemoteConnection("https://watch.example", store, client, browserMode = false).also { it.login("viewer", "fixture-password") }
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(ByteReadChannel(body), status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test fun aNativeSessionLooksUpApprovesAndDeniesWithItsBearerTokenAndNoBrowser() = runBlocking<Unit> {
        val calls = mutableListOf<Pair<String, String?>>()
        val remote = signedIn { request ->
            val body = request.body.toByteArray().decodeToString()
            calls += request.url.encodedPath to request.headers[HttpHeaders.Authorization]
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("12345678", Json.parseToJsonElement(body).jsonObject["user_code"]?.jsonPrimitive?.content)
            when (request.url.encodedPath) {
                "/api/auth/device/lookup" -> json(pending)
                "/api/auth/device/approve" -> json("""{"approved":true,"account_id":"1"}""")
                "/api/auth/device/deny" -> json("""{"denied":true}""")
                else -> error("Unexpected request ${request.url.encodedPath}")
            }
        }
        try {
            val approval = RemoteDeviceApproval(remote)
            val device = approval.lookup("12345678")
            assertEquals("Living room TV", device.deviceName)
            assertEquals("pending", device.status)
            approval.approve("12345678")
            approval.deny("12345678")
            assertEquals(listOf("/api/auth/device/lookup", "/api/auth/device/approve", "/api/auth/device/deny"), calls.map { it.first })
            assertTrue(calls.all { it.second == "Bearer access-1" })
        } finally { remote.close() }
    }

    @Test fun anInvalidCodeIsExplainedWithoutBlamingTheAccount() = runBlocking<Unit> {
        val remote = signedIn { json("""{"error":"Invalid or expired pairing code"}""", HttpStatusCode.BadRequest) }
        try {
            val failure = assertFailsWith<RemoteFailure> { RemoteDeviceApproval(remote).lookup("00000000") }
            assertEquals(400, failure.status)
            assertEquals("That code isn't valid or has expired. Check your TV and try again.", RemoteDeviceApproval.message(failure))
        } finally { remote.close() }
    }

    @Test fun aRestrictedProfileWithAPinMustUnlockParentControlsBeforeApprovingThenRetrySucceeds() = runBlocking<Unit> {
        var unlocked = false
        val remote = signedIn { request ->
            when (request.url.encodedPath) {
                "/api/auth/device/approve" -> if (unlocked) json("""{"approved":true}""") else json("{}", HttpStatusCode.Forbidden)
                "/api/parent/status" -> json("""{"pin_configured":true,"unlocked":$unlocked,"restricted":true}""")
                "/api/parent/unlock" -> { unlocked = true; json("""{"unlocked":true}""") }
                else -> error("Unexpected request ${request.url.encodedPath}")
            }
        }
        try {
            val approval = RemoteDeviceApproval(remote)
            assertFailsWith<ParentAuthorizationRequired> { approval.approve("12345678") }
            assertFailsWith<RemoteFailure> { approval.unlock("12") }
            approval.unlock("1234")
            approval.approve("12345678")
        } finally { remote.close() }
    }

    @Test fun aForbiddenWithoutAParentPinIsReportedAsAProfileRestriction() = runBlocking<Unit> {
        val remote = signedIn { request ->
            when (request.url.encodedPath) {
                "/api/auth/device/approve" -> json("{}", HttpStatusCode.Forbidden)
                "/api/parent/status" -> json("""{"pin_configured":false,"unlocked":false,"restricted":true}""")
                else -> error("Unexpected request ${request.url.encodedPath}")
            }
        }
        try {
            val failure = assertFailsWith<RemoteFailure> { RemoteDeviceApproval(remote).approve("12345678") }
            assertEquals("This profile can’t add devices. Switch to a parent profile and try again.", RemoteDeviceApproval.message(failure))
        } finally { remote.close() }
    }
}
