package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class StartupCancellationTest {
    @Test fun cancelWhileVaultReadIsPendingNeverStartsAConnectionOrRestoresPrivateState() = runBlocking<Unit> {
        val reading = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession {
                reading.complete(Unit)
                withContext(NonCancellable) { release.await() }
                return SavedRemoteSession("https://example.test", "fixture-r1")
            }
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var created = 0
        val controller = ApplicationController(scope, store, remoteFactory = { _, _ -> created++; error("Canceled restore opened transport") })
        try {
            controller.restore(); val restoring = requireNotNull(controller.job)
            withTimeout(2000) { reading.await() }
            controller.disconnect()
            assertEquals(Screen.Connect, controller.state.value.screen)
            assertFalse(controller.state.value.starting)
            release.complete(Unit)
            withTimeout(2000) { restoring.join() }
            assertEquals(0, created)
            assertNull(controller.state.value.home)
            assertTrue(controller.state.value.profiles.isEmpty())
            assertNull(controller.state.value.error)
        } finally { release.complete(Unit); controller.disconnect(); scope.cancel() }
    }

    @Test fun cancelDuringSingleUseRotationPreservesReplacementButDoesNotBootstrapOrEnterHome() = runBlocking<Unit> {
        val saving = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var saved = SavedRemoteSession("https://example.test", "fixture-r1")
        val store = object : RemoteSessionStore {
            override suspend fun read() = saved
            override suspend fun save(session: SavedRemoteSession) { saving.complete(Unit); release.await(); saved = session }
            override suspend fun clear() = Unit
        }
        var refreshes = 0; var bootstraps = 0
        val client = HttpClient(MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/auth/device/refresh" -> { refreshes++; """{"access_token":"fixture-a2","refresh_token":"fixture-r2"}""" }
                "/api/bootstrap" -> { bootstraps++; """{"identity":{"profiles":[],"profile_id":null},"home":null}""" }
                else -> error("Unexpected fixture request")
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val remote = RemoteConnection("https://example.test", store, client, browserMode = false)
        val controller = ApplicationController(scope, store, remoteFactory = { _, _ -> remote })
        try {
            controller.restore(); val restoring = requireNotNull(controller.job)
            withTimeout(2000) { saving.await() }
            controller.disconnect()
            assertEquals(Screen.Connect, controller.state.value.screen)
            assertFalse(controller.state.value.starting)
            release.complete(Unit)
            withTimeout(2000) { restoring.join(); remote.retire() }
            assertEquals("fixture-r2", saved.refreshToken)
            assertEquals(1, refreshes)
            assertEquals(0, bootstraps)
            assertNull(controller.state.value.home)
            assertTrue(controller.state.value.profiles.isEmpty())
            assertNull(controller.state.value.error)
        } finally { release.complete(Unit); controller.disconnect(); remote.close(); scope.cancel() }
    }
}
