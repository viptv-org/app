package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import kotlin.test.*

class RemoteBootstrapTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private suspend fun read(complete: Boolean): Bootstrap {
        val client = HttpClient(MockEngine {
            assertEquals("/api/bootstrap", it.url.encodedPath)
            respond("""{"identity":{"profiles":[{"id":1,"name":"Parent"}],"profile_id":1},"home":null,"integrations":[],"library_membership":{"identities":[{"id":"older-title","kind":"movie","ids":[]}],"revision":14,"complete":true},"library":{"cards":[],"complete":$complete},"preferences":{"revision":4,"preferences":{"subtitles_enabled":false,"source_selection":"best"}}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
        return try { connection.bootstrap() } finally { connection.close() }
    }
    @Test fun bootstrapConsumesPaginatedLibraryAndPreferencesInOneResponse() = runBlocking {
        val result = read(true)
        assertEquals(emptyList(), result.library)
        assertEquals(4L, result.preferences?.revision)
        assertEquals(false, result.preferences?.preferences?.subtitles_enabled)
        assertEquals("best", result.preferences?.preferences?.source_selection)
    }
    @Test fun partialBootstrapKeepsFirstPageAndCompleteMembershipWithoutHydratingOtherPages() = runBlocking {
        val result = read(false)
        assertEquals(emptyList(), result.library)
        assertEquals("older-title", result.libraryMembership?.identities?.single()?.id)
        assertEquals(true, result.libraryMembership?.complete)
    }
}
