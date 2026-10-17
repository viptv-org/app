package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class AccountDevicesTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = error("Browser sessions must not be persisted as device credentials")
        override suspend fun clear() = Unit
    }

    @Test fun browserDeviceManagementUsesOneAggregateAndCookieCsrfWithoutBearerCredentials(): Unit = runBlocking {
        val calls = mutableListOf<Pair<String, HttpMethod>>()
        val client = HttpClient(MockEngine { request ->
            calls += request.url.encodedPath to request.method
            assertNull(request.headers[HttpHeaders.Authorization])
            val body = when (request.url.encodedPath) {
                "/api/auth/login" -> """{"csrf_token":"fixture-csrf"}"""
                "/api/bootstrap" -> """{"identity":{"profiles":[],"profile_id":null},"home":null}"""
                "/api/account/devices" -> if (request.method == HttpMethod.Get) accountFixture else {
                    assertEquals("fixture-csrf",request.headers["x-csrf-token"]); """{"signed_out":true}"""
                }
                "/api/account/devices/other-family" -> {
                    assertEquals(HttpMethod.Delete,request.method)
                    assertEquals("fixture-csrf",request.headers["x-csrf-token"]); """{"signed_out":true}"""
                }
                else -> error("Unexpected account request")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType,"application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example",store,client,browserMode = true)
        try {
            remote.login("viewer","fixture-password")
            val data = remote.accountDevices()
            assertEquals("viewer",data.account.username)
            assertTrue(data.devices.single { it.current }.current)
            assertFalse(data.devices.single { it.current }.can_sign_out)
            assertEquals(1,calls.count { it.first == "/api/account/devices" })
            remote.signOutDevice("other-family"); remote.signOutAllDevices()
            assertEquals(listOf(HttpMethod.Get,HttpMethod.Delete),calls.filter { it.first == "/api/account/devices" }.map { it.second })
        } finally { remote.close() }
    }

    @Test fun nativeViewingConnectionsCannotDispatchAccountManagementRequests(): Unit = runBlocking {
        val remote = RemoteConnection("https://watch.example",store,HttpClient(MockEngine { error("No account request may leave a paired client") }),browserMode = false)
        try {
            assertFailsWith<IllegalStateException> { remote.accountDevices() }
            assertFailsWith<IllegalStateException> { remote.signOutDevice("other-family") }
            assertFailsWith<IllegalStateException> { remote.signOutAllDevices() }
            Unit
        } finally { remote.close() }
    }

    @Test fun oldHostDoesNotPretendItHasAnEmptyDeviceList(): Unit = runBlocking {
        val remote = RemoteConnection("https://watch.example",store,HttpClient(MockEngine {
            respond("{}",HttpStatusCode.NotFound,headersOf(HttpHeaders.ContentType,"application/json"))
        }),browserMode = true)
        try { assertEquals("Update the server to manage devices here.",assertFailsWith<RemoteFailure> { remote.accountDevices() }.message) }
        finally { remote.close() }
    }

    @Test fun aNewParentPolicyRefusalHidesPreviouslyLoadedDeviceData(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val remote = RemoteConnection("https://watch.example",store,HttpClient(MockEngine {
            respond("{}",HttpStatusCode.Forbidden,headersOf(HttpHeaders.ContentType,"application/json"))
        }),browserMode = true)
        try {
            val controller = ApplicationController(scope,store)
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Settings,
                accountDevices = kotlinx.serialization.json.Json.decodeFromString<AccountDevices>(accountFixture))
            controller.refreshAccountDevices(); controller.job?.join()
            assertNull(controller.state.value.accountDevices)
            assertNotNull(controller.state.value.error)
        } finally { scope.cancel(); remote.close() }
    }

    @Test fun signingOutOneDeviceReconcilesThatRowWithoutRefetchingTheAccount(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var requests = 0
        val remote = RemoteConnection("https://watch.example",store,HttpClient(MockEngine { request ->
            requests++
            assertEquals("/api/account/devices/other-family",request.url.encodedPath)
            assertEquals(HttpMethod.Delete,request.method)
            respond("""{"signed_out":true}""",headers = headersOf(HttpHeaders.ContentType,"application/json"))
        }),browserMode = true)
        try {
            val controller = ApplicationController(scope,store)
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Settings,
                accountDevices = kotlinx.serialization.json.Json.decodeFromString<AccountDevices>(accountFixture))
            controller.signOutDevice("other-family"); controller.job?.join()
            assertEquals(listOf("current-family"),controller.state.value.accountDevices?.devices?.map { it.id })
            assertEquals(1,requests)
            controller.signOutDevice("current-family"); controller.job?.join()
            assertEquals(1,requests,"Current browser must not dispatch per-device revocation")
        } finally { scope.cancel(); remote.close() }
    }
}

internal const val accountFixture = """{"account":{"id":"1","username":"viewer","name":"Viewer","role":"member"},"devices":[{"id":"other-family","name":"Living room TV","kind":"device","created_at":1791896400,"current":false,"can_sign_out":true},{"id":"current-family","name":"","kind":"browser","created_at":1791982800,"current":true,"can_sign_out":false}],"can_sign_out_all":true}"""
