package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.viptv.app.connection.*
import kotlin.test.*

class SessionCleanupOwnershipTest {
    private class Store : RemoteSessionStore {
        var saved: SavedRemoteSession? = null
        var navigation: String? = null
        var failReads = false
        var failWrites = false
        var clears = 0
        var connection: SavedConnection? = null
        private var locked = false
        override suspend fun <T> withSessionLock(block: suspend () -> T): T {
            check(!locked)
            locked = true
            return try { block() } finally { locked = false }
        }
        override suspend fun savedConnection(): SavedConnection = connection ?: read()?.let { SavedConnection.Remote(it) } ?: SavedConnection.None
        override suspend fun read(): SavedRemoteSession? { check(!failReads); return saved }
        override suspend fun save(session: SavedRemoteSession) { check(!failWrites); saved = session }
        override suspend fun clear() { check(locked); clears++; saved = null; navigation = null }
    }

    private class Account(var name: String, val store: Store) {
        var rejectRefresh = false
        var rejectLogout = false
        var familySupported = true
        private var refreshRevision = 0
        val bootstraps = EngineRequests<String>()
        val deletionStarted = CompletableDeferred<Unit>()
        var deletionGate: CompletableDeferred<Unit>? = null
        val deleted = EngineRequests<String>()
        val loggedOut = EngineRequests<String>()
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/auth/device/login" -> tokens()
                "/api/auth/device/refresh" -> {
                    if (rejectRefresh) return@MockEngine respond("{}", HttpStatusCode.Unauthorized)
                    refreshRevision++
                    tokens()
                }
                "/api/auth/account" -> {
                    deleted += request.headers[HttpHeaders.Authorization].orEmpty()
                    deletionStarted.complete(Unit)
                    deletionGate?.await()
                    return@MockEngine respond("", HttpStatusCode.NoContent)
                }
                "/api/auth/logout" -> {
                    loggedOut += request.headers[HttpHeaders.Authorization].orEmpty()
                    if (rejectLogout) return@MockEngine respond("{}", HttpStatusCode.ServiceUnavailable)
                    "{}"
                }
                "/api/bootstrap" -> {
                    bootstraps += request.headers[HttpHeaders.Authorization].orEmpty()
                    """{"identity":{"profiles":[],"profile_id":null},"home":null}"""
                }
                else -> error("Unexpected fixture request ${request.url.encodedPath}")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = false)
        private fun tokens() = buildJsonObject {
            put("access_token", "access-$name")
            put("refresh_token", "refresh-$name-$refreshRevision")
            if (familySupported) put("session_id", "family-$name")
        }.toString()
        suspend fun login() { remote.login(name, "fixture-password") }
        suspend fun delete() { remote.deleteAccount(name, "fixture-password", null); remote.forgetDeletedAccount() }
    }

    @Test fun oldAccountDeletionCannotClearTheNewInstancesSavedSessionOrNavigation() = runBlocking {
        val store = Store()
        val old = Account("old", store)
        val replacement = Account("replacement", store)
        val gate = CompletableDeferred<Unit>()
        old.deletionGate = gate
        try {
            old.login()
            val deleting = async { old.delete() }
            old.deletionStarted.await()
            replacement.login()
            store.navigation = "replacement-home"
            val saved = store.saved
            gate.complete(Unit); deleting.await()
            assertEquals(listOf("Bearer access-old"), old.deleted)
            assertEquals(saved, store.saved)
            assertEquals("replacement-home", store.navigation)
            assertEquals(0, store.clears)
        } finally { gate.complete(Unit); old.remote.close(); replacement.remote.close() }
    }

    @Test fun rejectedUnpersistedSessionSignOutPreservesThePreviousSavedAccount() = runBlocking {
        val store = Store()
        val account = Account("saved", store)
        try {
            account.login()
            val saved = store.saved
            store.navigation = "saved-home"
            store.failWrites = true
            account.name = "memory-only"
            account.login()
            account.rejectRefresh = true
            account.remote.signOut()
            assertEquals(saved, store.saved)
            assertEquals("saved-home", store.navigation)
            assertEquals(0, store.clears)
            account.remote.bootstrap()
            assertEquals("", account.bootstraps.last())
        } finally { account.remote.close() }
    }

    @Test fun ownDeletionAndSignOutStillClearSavedSessionAndNavigation() = runBlocking {
        for (deleting in listOf(true, false)) {
            val store = Store()
            val account = Account("own", store)
            try {
                account.login(); store.navigation = "own-home"
                if (deleting) account.delete() else account.remote.signOut()
                assertNull(store.saved); assertNull(store.navigation)
                assertEquals(1, store.clears)
                account.remote.bootstrap()
                assertEquals("", account.bootstraps.last())
                if (!deleting) assertEquals(listOf("Bearer access-own"), account.loggedOut)
            } finally { account.remote.close() }
        }
    }

    @Test fun unavailableStorageDoesNotPreventMemoryOnlySessionCleanupOrClearUnknownData() = runBlocking {
        for (deleting in listOf(true, false)) {
            val store = Store().apply { failReads = true; failWrites = true }
            val account = Account("memory-only", store)
            try {
                account.login()
                if (deleting) account.delete() else account.remote.signOut()
                assertEquals(0, store.clears)
                account.remote.bootstrap()
                assertEquals("", account.bootstraps.last())
                if (!deleting) assertEquals(listOf("Bearer access-memory-only"), account.loggedOut)
            } finally { account.remote.close() }
        }
    }

    @Test fun cleanupChecksFamilyOriginAndConnectionChoiceUnderTheSessionLock() = runBlocking {
        val replacements = listOf(
            SavedConnection.Remote(SavedRemoteSession("https://watch.example", "rotated", "family-own")) to true,
            SavedConnection.Remote(SavedRemoteSession("https://watch.example", "replacement", "family-other")) to false,
            SavedConnection.Remote(SavedRemoteSession("https://other.example", "refresh-own", "family-own")) to false,
            SavedConnection.Local to false,
            SavedConnection.None to false,
        )
        for ((replacement, owned) in replacements) {
            val store = Store()
            val account = Account("own", store)
            try {
                account.login(); store.navigation = "saved-home"
                store.connection = replacement
                account.delete()
                assertEquals(if (owned) 1 else 0, store.clears, "$replacement")
                assertEquals(if (owned) null else "saved-home", store.navigation)
            } finally { account.remote.close() }
        }
    }

    @Test fun anUninitializedConnectionCannotEraseAnUnidentifiedSavedSession() = runBlocking {
        val store = Store().apply { saved = SavedRemoteSession("https://watch.example", "unrelated") }
        val account = Account("unknown", store)
        try {
            account.remote.forgetDeletedAccount()
            assertNotNull(store.saved)
            assertEquals(0, store.clears)
        } finally { account.remote.close() }
    }

    @Test fun aFailedLogoutKeepsItsOwnedCredentialAndLocalSession() = runBlocking {
        val store = Store()
        val account = Account("own", store)
        try {
            account.login(); account.rejectLogout = true
            assertEquals(503, assertFailsWith<RemoteFailure> { account.remote.signOut() }.status)
            assertNotNull(store.saved)
            assertEquals(0, store.clears)
            account.remote.bootstrap()
            assertEquals("Bearer access-own", account.bootstraps.last())
        } finally { account.remote.close() }
    }


    @Test fun legacySignOutClearsItsOwnedPreRotationRecordAfterPersistenceFails() = runBlocking {
        val store = Store()
        val account = Account("legacy", store).apply { familySupported = false }
        try {
            account.login()
            store.failWrites = true
            account.remote.signOut()
            assertNull(store.saved)
            assertEquals(1, store.clears)
            assertEquals(listOf("Bearer access-legacy"), account.loggedOut)
        } finally { account.remote.close() }
    }

}
