package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class SourceIntegrationRecoveryTest {
    private class Store : RemoteSessionStore {
        var saved: SavedRemoteSession? = null
        override suspend fun read() = saved
        override suspend fun save(session: SavedRemoteSession) { saved = session }
        override suspend fun clear() { saved = null }
    }
    private val retained = SourceIntegrationSnapshot(listOf(SourceIntegrationSchema("fixture", "Fixture source", "Ready", true)), 7)
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test fun expiredSourceIntegrationSessionReturnsToSignInInsteadOfRetryingRetiredCredentials() = runBlocking {
        val store = Store()
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val status = if (path.endsWith("source-integrations") || path == "/api/auth/device/refresh") HttpStatusCode.Unauthorized else HttpStatusCode.OK
            val body = when (path) {
                "/api/auth/device/login" -> """{"access_token":"fixture-access","refresh_token":"fixture-refresh"}"""
                "/api/bootstrap" -> """{"identity":{"profiles":[],"profile_id":null},"home":null}"""
                else -> "{}"
            }
            respond(body, status, jsonHeaders)
        }) { configureTransport() }, browserMode = false)
        val controller = ApplicationController(this, store)
        try {
            remote.login("fixture", "fixture")
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "profile", sourceIntegrations = retained)
            controller.refreshSourceIntegrations(); controller.job!!.join()
            assertNull(store.saved)
            assertNull(controller.connection)
            assertEquals(Screen.Connect, controller.state.value.screen)
            assertEquals("Session expired. Sign in again.", controller.state.value.error)
            assertNull(controller.state.value.sourceIntegrationError)
        } finally { remote.close() }
    }

    @Test fun forbiddenSourceSettingsKeepPermissionCopyAndTheRetainedSnapshot() = runBlocking {
        val store = Store()
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine {
            respond("{}", HttpStatusCode.Forbidden, jsonHeaders)
        }) { configureTransport() }, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "profile", sourceIntegrations = retained)
        try {
            controller.refreshSourceIntegrations(); controller.job!!.join()
            assertSame(remote, controller.connection)
            assertEquals(Screen.Settings, controller.state.value.screen)
            assertEquals(retained, controller.state.value.sourceIntegrations)
            assertEquals("This action is not available for this profile.", controller.state.value.sourceIntegrationError)
            assertNull(controller.state.value.error)
        } finally { remote.close() }
    }

    @Test fun temporaryFailureRetainsConfigurationAndSuccessfulRetryClearsOnlyItsError() = runBlocking {
        var requests = 0
        val store = Store()
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine {
            requests++
            if (requests == 1) respond("{}", HttpStatusCode.ServiceUnavailable, jsonHeaders)
            else respond("""{"revision":9,"schemas":[],"connections":[]}""", headers = jsonHeaders)
        }) { configureTransport() }, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "profile", sourceIntegrations = retained)
        try {
            controller.refreshSourceIntegrations(); controller.job!!.join()
            assertEquals(retained, controller.state.value.sourceIntegrations)
            assertEquals("Could not load source integrations. Try again.", controller.state.value.sourceIntegrationError)
            controller.refreshSourceIntegrations(); controller.job!!.join()
            assertEquals(2, requests)
            assertEquals(9L, controller.state.value.sourceIntegrations.revision)
            assertTrue(controller.state.value.sourceIntegrations.schemas.isEmpty())
            assertNull(controller.state.value.sourceIntegrationError)
            assertSame(remote, controller.connection)
        } finally { remote.close() }
    }

    @Test fun cancelledIntegrationLoadDoesNotBecomeAVisibleFailureOrReplaceTheSnapshot() = runBlocking {
        val started = CompletableDeferred<Unit>(); val store = Store()
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine {
            started.complete(Unit); awaitCancellation()
        }) { configureTransport() }, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "profile", sourceIntegrations = retained,
            sourceIntegrationError = "Previous failure")
        try {
            controller.refreshSourceIntegrations(); started.await(); controller.job!!.cancelAndJoin()
            assertTrue(controller.job!!.isCancelled)
            assertEquals(retained, controller.state.value.sourceIntegrations)
            assertEquals("Previous failure", controller.state.value.sourceIntegrationError)
            assertNull(controller.state.value.error)
        } finally { remote.close() }
    }
}
