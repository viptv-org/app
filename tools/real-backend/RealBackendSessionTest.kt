package org.viptv.app

import java.io.File
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import kotlin.test.*

/** Opt-in only: reads private credentials from VIPTV_REAL_CREDENTIALS_FILE. */
class RealBackendSessionTest {
    @Test fun realSessionRestoresAndSearches() = runBlocking<Unit> {
        val config = Json.parseToJsonElement(File(requireNotNull(System.getenv("VIPTV_REAL_CREDENTIALS_FILE"))).readText()).jsonObject
        val origin = requireNotNull(System.getenv("VIPTV_REAL_ORIGIN"))
        val profile = System.getenv("VIPTV_REAL_PROFILE") ?: "1"
        // Isolates this test's device credentials from the user's desktop wallet.
        val store = object : RemoteSessionStore {
            var saved: SavedRemoteSession? = null
            val observedTokens = mutableSetOf<String>()
            var reusedToken = false
            override suspend fun read() = saved
            override suspend fun save(session: SavedRemoteSession) {
                if (!observedTokens.add(session.refreshToken)) reusedToken = true
                saved = session
            }
            override suspend fun clear() { saved = null }
        }
        var remote = RemoteConnection(origin, store, browserMode = false)
        val rotated = mutableSetOf<String>()
        try {
            val login = remote.login(config.getValue("username").jsonPrimitive.content, config.getValue("password").jsonPrimitive.content)
            assertTrue(login.profiles.any { it.id == profile }, "Real profile missing")
            assertEquals(profile, remote.selectProfile(profile).selected)
            rotated.add(requireNotNull(store.read()).refreshToken)
            repeat(5) {
                remote.retire()
                remote = RemoteConnection(origin, store, browserMode = false)
                assertEquals(profile, remote.restoreFromStore().selected)
                assertTrue(rotated.add(requireNotNull(store.read()).refreshToken), "Refresh token was reused")
                coroutineScope { List(8) { async { assertEquals(profile, remote.bootstrap().selected) } }.awaitAll() }
            }
            coroutineScope {
                List(2) { async { assertEquals(profile, remote.restoreFromStore().selected) } }.awaitAll()
            }
            assertFalse(store.reusedToken, "A consumed refresh token was persisted again")
            val cards = remote.search("Ted Lasso").cards
            assertTrue(cards.isNotEmpty(), "Real Simkl search returned no cards")
            remote.detail(cards.first())
            println("REAL_BACKEND_KMP restores=5 concurrent_restores=2 unique_refresh_tokens=${store.observedTokens.size} parallel_bootstraps=40 search_cards=${cards.size} detail_loaded=true")
        } finally {
            try { remote.signOut() } finally { remote.retire() }
        }
        assertNull(store.read(), "Signout must clear this test's device session")
    }
}
