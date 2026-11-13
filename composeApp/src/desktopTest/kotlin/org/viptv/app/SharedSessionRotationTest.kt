package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import kotlin.test.*

class SharedSessionRotationTest {
    private class Store : RemoteSessionStore {
        var saved: SavedRemoteSession? = null
        var failReads = false
        var failWrites = false
        override suspend fun read(): SavedRemoteSession? { check(!failReads); return saved }
        override suspend fun save(session: SavedRemoteSession) { check(!failWrites); saved = session }
        override suspend fun clear() { saved = null }
    }

    private class Fixture {
        val store = Store()
        var revision = 1
        var rejectRefresh = false
        var family: String? = "family"
        val expired = mutableSetOf<String>()
        val submitted = mutableListOf<String>()
        private val bootstrap = """{"identity":{"profiles":[],"profile_id":null},"home":null}"""
        private fun tokens() = buildJsonObject {
            family?.let { put("session_id", it) }
            put("access_token", "a$revision"); put("refresh_token", "r$revision")
        }.toString()
        fun expire() { expired += "Bearer a$revision" }
        fun remote() = RemoteConnection("https://watch.example", store, HttpClient(MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/auth/device/login" -> tokens()
                "/api/auth/device/refresh" -> {
                    val supplied = Json.parseToJsonElement((request.body as TextContent).text).jsonObject.getValue("refresh_token").jsonPrimitive.content
                    submitted += supplied
                    if (rejectRefresh || supplied != "r$revision") return@MockEngine respond("{}", HttpStatusCode.Unauthorized)
                    revision++
                    tokens()
                }
                else -> {
                    val access = request.headers[HttpHeaders.Authorization]
                    if (access != "Bearer a$revision" || access in expired)
                        return@MockEngine respond("{}", HttpStatusCode.Unauthorized)
                    bootstrap
                }
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = false)
    }

    @Test fun activeClientUsesTheReplacementCredentialAfterAnotherInstanceRestores() = runBlocking<Unit> {
        val fixture = Fixture()
        val active = fixture.remote()
        val second = fixture.remote()
        try {
            active.login("fixture", "fixture-password")
            second.restoreFromStore()
            active.bootstrap()
            assertEquals(listOf("r1", "r2"), fixture.submitted)
            assertEquals("r3", fixture.store.saved?.refreshToken)
        } finally { active.close(); second.close() }
    }

    @Test fun aReplacementAccountIsNotAdoptedOrCleared() = runBlocking<Unit> {
        val fixture = Fixture()
        val active = fixture.remote()
        try {
            active.login("fixture", "fixture-password")
            val replacement = SavedRemoteSession("https://watch.example", "other-token", "other-family")
            fixture.store.saved = replacement
            fixture.expire()
            assertFailsWith<LocalFailure> { active.bootstrap() }
            assertEquals(emptyList(), fixture.submitted)
            assertEquals(replacement, fixture.store.saved)
        } finally { active.close() }
    }

    @Test fun clearedSavedConnectionIsNeverResurrectedByAnActiveClient() = runBlocking<Unit> {
        val fixture = Fixture()
        val active = fixture.remote()
        try {
            active.login("fixture", "fixture-password")
            fixture.store.clear()
            fixture.expire()
            assertFailsWith<LocalFailure> { active.bootstrap() }
            assertEquals(emptyList(), fixture.submitted)
            assertNull(fixture.store.saved)
        } finally { active.close() }
    }

    @Test fun unreadableStorageNeverSendsAPotentiallyConsumedToken() = runBlocking<Unit> {
        val fixture = Fixture()
        val active = fixture.remote()
        try {
            active.login("fixture", "fixture-password")
            fixture.store.failReads = true
            fixture.expire()
            assertFailsWith<LocalFailure> { active.bootstrap() }
            assertEquals(emptyList(), fixture.submitted)
            assertEquals("r1", fixture.store.saved?.refreshToken)
        } finally { active.close() }
    }

    @Test fun failedPersistenceDoesNotReplaceTheInMemorySuccessorWithItsConsumedPredecessor() = runBlocking<Unit> {
        val fixture = Fixture()
        val active = fixture.remote()
        try {
            active.login("fixture", "fixture-password")
            fixture.store.failWrites = true
            fixture.expire(); active.bootstrap()
            fixture.expire(); active.bootstrap()
            assertEquals(listOf("r1", "r2"), fixture.submitted)
            assertEquals("r1", fixture.store.saved?.refreshToken)
            assertNotNull(active.persistenceWarning)
        } finally { active.close() }
    }

    @Test fun rejectedUnpersistedLoginDoesNotClearThePreviousAccountRecord() = runBlocking<Unit> {
        val fixture = Fixture()
        val active = fixture.remote()
        try {
            active.login("fixture", "fixture-password")
            val previous = fixture.store.saved
            fixture.store.failWrites = true
            fixture.family = "new-family"; fixture.revision = 10
            active.login("new-fixture", "fixture-password")
            fixture.expire(); fixture.rejectRefresh = true
            assertTrue(assertFailsWith<RemoteFailure> { active.bootstrap() }.sessionExpired)
            assertEquals(previous, fixture.store.saved)
        } finally { active.close() }
    }

    @Test fun legacySavedCredentialsAcquireTheirFamilyOnRestore() = runBlocking<Unit> {
        val fixture = Fixture()
        fixture.store.saved = SavedRemoteSession("https://watch.example", "r1")
        val active = fixture.remote()
        try {
            active.restoreFromStore()
            assertEquals("family", fixture.store.saved?.sessionId)
            assertEquals("r2", fixture.store.saved?.refreshToken)
        } finally { active.close() }
    }

    @Test fun anActiveLegacySessionNeverAdoptsAnUnidentifiedReplacement() = runBlocking<Unit> {
        val fixture = Fixture().apply { family = null }
        val active = fixture.remote()
        try {
            active.login("fixture", "fixture-password")
            val replacement = SavedRemoteSession("https://watch.example", "other-token")
            fixture.store.saved = replacement
            fixture.expire()
            assertFailsWith<LocalFailure> { active.bootstrap() }
            assertEquals(emptyList(), fixture.submitted)
            assertEquals(replacement, fixture.store.saved)
        } finally { active.close() }
    }
}
