package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class RemoteSessionConcurrencyTest {
    private class Store : RemoteSessionStore {
        var saved: SavedRemoteSession? = null
        var saving: CompletableDeferred<Unit>? = null
        var release: CompletableDeferred<Unit>? = null
        override suspend fun read() = saved
        override suspend fun save(session: SavedRemoteSession) {
            if (session.refreshToken == "r2") { saving?.complete(Unit); release?.await() }
            saved = session
        }
        override suspend fun clear() { saved = null }
    }
    private val bootstrap = """{"identity":{"profiles":[],"profile_id":null},"home":null}"""

    @Test fun parallelUnauthorizedRequestsConsumeTheRefreshTokenOnce() = runBlocking {
        val store = Store()
        var refreshes = 0
        var expired = false
        val client = HttpClient(MockEngine { request ->
            val (body, status) = when (request.url.encodedPath) {
                "/api/auth/device/login" -> """{"access_token":"a1","refresh_token":"r1"}""" to HttpStatusCode.OK
                "/api/auth/device/refresh" -> {
                    refreshes++
                    delay(30)
                    """{"access_token":"a2","refresh_token":"r2"}""" to HttpStatusCode.OK
                }
                else -> if (expired && request.headers[HttpHeaders.Authorization] == "Bearer a1")
                    "{}" to HttpStatusCode.Unauthorized else bootstrap to HttpStatusCode.OK
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            remote.login("member", "password")
            expired = true
            coroutineScope { List(20) { async { remote.bootstrap() } }.awaitAll() }
            assertEquals(1, refreshes)
            assertEquals("r2", store.saved?.refreshToken)
        } finally { remote.close() }
    }

    @Test fun cancellationDuringRotationPersistsReplacementBeforeNextRequest() = runBlocking {
        val store = Store()
        var expired = false
        var refreshes = 0
        val client = HttpClient(MockEngine { request ->
            val (body, status) = when (request.url.encodedPath) {
                "/api/auth/device/login" -> """{"access_token":"a1","refresh_token":"r1"}""" to HttpStatusCode.OK
                "/api/auth/device/refresh" -> {
                    refreshes++
                    """{"access_token":"a2","refresh_token":"r2"}""" to HttpStatusCode.OK
                }
                else -> if (expired && request.headers[HttpHeaders.Authorization] == "Bearer a1")
                    "{}" to HttpStatusCode.Unauthorized else bootstrap to HttpStatusCode.OK
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            remote.login("member", "password")
            store.saving = CompletableDeferred(); store.release = CompletableDeferred()
            expired = true
            val cancelled = launch { remote.bootstrap() }
            withTimeout(2_000) { store.saving!!.await() }
            cancelled.cancel()
            store.release!!.complete(Unit)
            cancelled.join()
            assertTrue(cancelled.isCancelled)
            assertEquals("r2", store.saved?.refreshToken)
            remote.bootstrap()
            assertEquals(1, refreshes, "The cancelled request must install its rotated access token")
        } finally { remote.close() }
    }
    @Test fun retiredRefreshCannotOverwriteAReplacementAccount() = runBlocking<Unit> {
        val store = Store()
        var expired = false
        fun client(account: String) = HttpClient(MockEngine { request ->
            val (body, status) = when (request.url.encodedPath) {
                "/api/auth/device/login" -> """{"access_token":"${account}a1","refresh_token":"${account}r1"}""" to HttpStatusCode.OK
                "/api/auth/device/refresh" -> """{"access_token":"a2","refresh_token":"r2"}""" to HttpStatusCode.OK
                else -> if (expired && request.headers[HttpHeaders.Authorization] == "Bearer olda1")
                    "{}" to HttpStatusCode.Unauthorized else bootstrap to HttpStatusCode.OK
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val old = RemoteConnection("https://watch.example", store, client("old"), browserMode = false)
        val replacement = RemoteConnection("https://watch.example", store, client("new"), browserMode = false)
        try {
            old.login("old", "password")
            store.saving = CompletableDeferred(); store.release = CompletableDeferred(); expired = true
            val refreshing = launch { old.bootstrap() }
            withTimeout(2_000) { store.saving!!.await() }
            old.close()
            val signingIn = async { replacement.login("new", "password") }
            yield()
            assertFalse(signingIn.isCompleted)
            store.release!!.complete(Unit)
            refreshing.join(); signingIn.await(); old.retire()
            assertEquals("newr1", store.saved?.refreshToken)
            replacement.bootstrap()
            assertFailsWith<CancellationException> { old.bootstrap() }
        } finally { store.release?.complete(Unit); old.close(); replacement.close() }
    }

    @Test fun cancelledSignOutStillRetiresTheRevokedControllerSession() = runBlocking {
        val store = Store()
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/auth/device/login" -> """{"access_token":"a1","refresh_token":"r1"}"""
                "/api/auth/device/refresh" -> """{"access_token":"a2","refresh_token":"r2"}"""
                "/api/auth/logout" -> { started.complete(Unit); release.await(); "{}" }
                else -> bootstrap
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        try {
            remote.login("member", "password")
            controller.connection = remote
            controller.mutable.value = ApplicationState(screen = Screen.Profiles)
            controller.signOut()
            val signingOut = requireNotNull(controller.job)
            withTimeout(2_000) { started.await() }
            signingOut.cancel()
            release.complete(Unit)
            withTimeout(2_000) { signingOut.join() }
            assertNull(controller.connection)
            assertNull(store.saved)
            assertEquals(ApplicationState(), controller.state.value)
        } finally { release.complete(Unit); remote.close() }
    }

    @Test fun enteringLocalModeWaitsForRotationBeforeClearingTheSavedRemoteSession() = runBlocking {
        val store = Store()
        var expired = false
        var localOpened = false
        val client = HttpClient(MockEngine { request ->
            val (body, status) = when (request.url.encodedPath) {
                "/api/auth/device/login" -> """{"access_token":"a1","refresh_token":"r1"}""" to HttpStatusCode.OK
                "/api/auth/device/refresh" -> """{"access_token":"a2","refresh_token":"r2"}""" to HttpStatusCode.OK
                else -> if (expired && request.headers[HttpHeaders.Authorization] == "Bearer a1")
                    "{}" to HttpStatusCode.Unauthorized else bootstrap to HttpStatusCode.OK
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store, localFactory = {
            localOpened = true
            throw LocalFailure("Fixture stops after credential cleanup")
        })
        try {
            remote.login("member", "password")
            store.saving = CompletableDeferred(); store.release = CompletableDeferred(); expired = true
            controller.connection = remote
            val hydration = launch { remote.bootstrap() }
            withTimeout(2_000) { store.saving!!.await() }
            controller.useLocal("fixture.sqlite")
            yield()
            assertFalse(localOpened)
            store.release!!.complete(Unit)
            hydration.join(); controller.job!!.join()
            assertTrue(localOpened)
            assertNull(store.saved, "A late remote refresh must not restore credentials after entering local mode")
        } finally { store.release?.complete(Unit); remote.close() }
    }

}
