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

class StartupRefreshHandoffTest {
    @Test fun changedConnectionOrLogoutBeforeTheGateDoesNotDispatchOrEraseItsReplacement() = runBlocking<Unit> {
        for (replacement in listOf(SavedConnection.None, SavedConnection.Local,
            SavedConnection.Remote(SavedRemoteSession("https://other.example", "other-account")))) {
            var current: SavedConnection = SavedConnection.Remote(SavedRemoteSession("https://fixture.example", "r1"))
            var requests = 0
            val store = object : RemoteSessionStore {
                override suspend fun read() = (current as? SavedConnection.Remote)?.session
                override suspend fun savedConnection() = current
                override suspend fun save(session: SavedRemoteSession) { current = SavedConnection.Remote(session) }
                override suspend fun clear() { current = SavedConnection.None }
            }
            val controller = ApplicationController(this, store, remoteFactory = { origin, storage ->
                current = replacement
                RemoteConnection(origin, storage, HttpClient(MockEngine { requests++; error("Changed startup dispatched credentials") }) { configureTransport() }, browserMode = false)
            })
            try {
                controller.restore(); requireNotNull(controller.job).join()
                assertEquals(0, requests); assertEquals(replacement, current)
                assertNull(controller.connection); assertNull(controller.state.value.home)
                assertContains(controller.state.value.error.orEmpty(), "saved connection changed")
            } finally { controller.disconnect() }
        }
    }

    @Test fun failureToRevalidateSecureStorageNeverFallsBackToThePossiblyConsumedSnapshot() = runBlocking<Unit> {
        var reads = 0; var requests = 0
        val initial = SavedRemoteSession("https://fixture.example", "r1")
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession { if (++reads > 1) error("Fixture vault unavailable"); return initial }
            override suspend fun save(session: SavedRemoteSession) = error("Must not persist stale rotation")
            override suspend fun clear() = error("Must not clear unavailable vault")
        }
        val controller = ApplicationController(this, store, remoteFactory = { origin, storage ->
            RemoteConnection(origin, storage, HttpClient(MockEngine { requests++; error("Dispatched stale snapshot") }) { configureTransport() }, browserMode = false)
        })
        try {
            controller.restore(); requireNotNull(controller.job).join()
            assertEquals(0, requests); assertNull(controller.connection)
            assertContains(controller.state.value.error.orEmpty(), "Secure storage is unavailable")
        } finally { controller.disconnect() }
    }

    @Test fun replacementControllerNeverReplaysSnapshotReadDuringPreviousRotation() = runBlocking<Unit> {
        val saving = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val reread = CompletableDeferred<Unit>()
        var saved: SavedRemoteSession? = SavedRemoteSession("https://fixture.example", "r1")
        var captureRead = false
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = saved.also { if (captureRead) reread.complete(Unit) }
            override suspend fun save(session: SavedRemoteSession) {
                if (session.refreshToken == "r2") { saving.complete(Unit); release.await() }
                saved = session
            }
            override suspend fun clear() { saved = null }
        }
        val tokens = mutableListOf<String>()
        fun client() = HttpClient(MockEngine { request ->
            val body = if (request.url.encodedPath.endsWith("/refresh")) {
                val token = Json.parseToJsonElement((request.body as TextContent).text).jsonObject.getValue("refresh_token").jsonPrimitive.content
                val duplicate = synchronized(tokens) { val seen = token in tokens; tokens += token; seen }
                if (duplicate) return@MockEngine respond("{}", HttpStatusCode.Unauthorized)
                val next = if (token == "r1") "r2" else "r3"
                """{"access_token":"access-$next","refresh_token":"$next"}"""
            } else """{"identity":{"profiles":[],"profile_id":null},"home":null}"""
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val old = RemoteConnection("https://fixture.example", store, client(), browserMode = false)
        val controller = ApplicationController(this, store, remoteFactory = { origin, storage ->
            RemoteConnection(origin, storage, client(), browserMode = false)
        })
        val rotation = async { runCatching { old.restore(requireNotNull(saved)) } }
        try {
            withTimeout(2000) { saving.await() }
            captureRead = true
            controller.restore()
            withTimeout(2000) { reread.await() }
            old.close()
            release.complete(Unit)
            withTimeout(2000) { rotation.await(); requireNotNull(controller.job).join() }
            assertEquals(listOf("r1", "r2"), tokens)
            assertEquals("r3", saved?.refreshToken)
            assertNull(controller.state.value.error)
            assertEquals(Screen.Profiles, controller.state.value.screen)
        } finally { release.complete(Unit); rotation.cancelAndJoin(); old.retire(); controller.disconnect() }
    }
}
