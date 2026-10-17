package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlinx.serialization.json.*
import kotlin.test.*

class AccountEntryTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = error("Browser recovery material must never enter device storage")
        override suspend fun clear() = Unit
    }
    private val created = """{"csrf_token":"new-csrf","recovery_codes":["fixture-private-recovery"]}"""
    private val bootstrap = """{"identity":{"profiles":[],"profile_id":null},"home":null}"""

    @Test fun registrationShowsOneTimeCodeBeforeAnyBootstrapAndRetryNeverRegistersAgain() = runBlocking {
        val paths = mutableListOf<String>()
        var reads = 0
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            val (body, status) = if (request.url.encodedPath == "/api/auth/register") {
                val value = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals("member", value["username"]?.jsonPrimitive?.content)
                created to HttpStatusCode.OK
            } else {
                assertEquals("new-csrf", request.headers["x-csrf-token"])
                reads++
                if (reads == 1) "{}" to HttpStatusCode.ServiceUnavailable else bootstrap to HttpStatusCode.OK
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = true)
        val controller = ApplicationController(this, store); controller.connection = remote
        try {
            controller.registerAccount("https://watch.example", " member ", "Member", "a long test password")
            controller.job!!.join()
            assertEquals(listOf("/api/auth/register"), paths)
            assertEquals(Screen.RecoveryCodes, controller.state.value.screen)
            assertEquals("fixture-private-recovery", controller.state.value.recoveryKit?.codes?.single())
            assertFalse(controller.state.value.toString().contains("fixture-private-recovery"))
            controller.finishAccountEntry(); controller.job!!.join()
            assertEquals(Screen.RecoveryCodes, controller.state.value.screen)
            assertNotNull(controller.state.value.recoveryKit)
            controller.finishAccountEntry(); controller.job!!.join()
            assertEquals(Screen.Profiles, controller.state.value.screen)
            assertNull(controller.state.value.recoveryKit)
            assertEquals(1, paths.count { it == "/api/auth/register" })
            assertTrue(controller.state.value.profiles.isEmpty())
        } finally { remote.close() }
    }

    @Test fun recoverySubmitsTheSavedCodeAndKeepsOnlyTheReplacementInMemory() = runBlocking {
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine { request ->
            assertEquals("/api/auth/recover", request.url.encodedPath)
            val value = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("old-code", value["recovery_code"]?.jsonPrimitive?.content)
            assertEquals("replacement-password", value["password"]?.jsonPrimitive?.content)
            respond(created, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = true)
        val controller = ApplicationController(this, store); controller.connection = remote
        try {
            controller.recoverAccount("https://watch.example", "member", "old-code", "replacement-password"); controller.job!!.join()
            assertEquals(true, controller.state.value.recoveryKit?.recovered)
            assertEquals(Screen.RecoveryCodes, controller.state.value.screen)
            assertFalse(controller.state.value.toString().contains("old-code"))
        } finally { remote.close() }
    }

    @Test fun duplicateRegistrationCannotCreateTwoRequests() = runBlocking {
        var requests = 0
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine {
            requests++; started.complete(Unit); release.await()
            respond(created, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = true)
        val controller = ApplicationController(this, store); controller.connection = remote
        try {
            controller.registerAccount("https://watch.example", "member", "Member", "a long test password"); started.await()
            controller.registerAccount("https://watch.example", "member", "Member", "a long test password")
            release.complete(Unit); controller.job!!.join()
            controller.registerAccount("https://watch.example", "member", "Member", "a long test password")
            assertEquals(1, requests)
        } finally { release.complete(Unit); remote.close() }
    }

    @Test fun accountInputValidationMatchesExistingServerBoundaries() {
        assertTrue(validAccountUsername(" member@example "))
        assertFalse(validAccountUsername("member name")); assertFalse(validAccountUsername("用户"))
        assertTrue(validNewPassword("密碼測試")) // Existing server checks UTF-8 bytes.
        assertFalse(validNewPassword("short")); assertFalse(validNewPassword("a".repeat(257)))
        assertFailsWith<RemoteFailure> { recoveryKit(JsonObject(emptyMap()), false) }
    }
}
