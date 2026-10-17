package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class AccountIdentityTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val response = """{"account":{"id":"1","username":"viewer","name":"Viewer Name","role":"member"},"csrf_token":"current"}"""

    @Test fun nativeAccountScreenReadsIdentityOnceWithoutBrowserManagementOrSimklCalls() = runBlocking {
        val requests = mutableListOf<String>()
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            requests += request.url.encodedPath
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "profile")
        try {
            controller.refreshAccounts(); controller.job!!.join()
            assertEquals(listOf("/api/auth/me"), requests)
            assertEquals("viewer", controller.state.value.accountIdentity?.username)
            assertNull(controller.state.value.simkl)
            assertFalse(remote.accountManagementEnabled)
        } finally { remote.close() }
    }

    @Test fun failedNativeIdentityLoadCanRetryWithoutExtraManagementCalls() = runBlocking<Unit> {
        val paths = mutableListOf<String>()
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            if (paths.size == 1) respond("private upstream diagnostic", HttpStatusCode.ServiceUnavailable)
            else respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "profile")
        try {
            controller.refreshAccountIdentity(); controller.job!!.join()
            assertNull(controller.state.value.accountIdentity); assertNotNull(controller.state.value.error)
            assertFalse(controller.state.value.error.orEmpty().contains("private")); assertFalse(controller.state.value.busy)
            assertEquals("profile", controller.state.value.selectedProfile)
            controller.refreshAccountIdentity(); controller.job!!.join()
            assertEquals("viewer", controller.state.value.accountIdentity?.username); assertNull(controller.state.value.error)
            assertEquals(listOf("/api/auth/me", "/api/auth/me"), paths)
            assertNull(controller.state.value.simkl); assertNull(controller.state.value.accountDevices)
        } finally { remote.close() }
    }

    @Test fun identityResponseCannotCrossAConnectionReplacement() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine {
            started.complete(Unit); release.await()
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        try {
            controller.refreshAccountIdentity(); started.await()
            controller.connection = null
            controller.mutable.value = ApplicationState()
            release.complete(Unit); controller.job!!.join()
            assertNull(controller.state.value.accountIdentity)
        } finally { release.complete(Unit); remote.close() }
    }
}
