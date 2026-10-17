package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class FavoriteLookupIntentTest {
    @Test fun addFromAnUnverifiedPreviewStaysAddAndAdoptsOnlyHostReturnedAssertions() = runBlocking<Unit> {
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
        val preview = Card(MediaIdentity("private:one", "movie", emptyList()), provider_id = "addon:7", title = "Preview", badges = emptyList(), metadata_origin = origin)
        val verified = preview.copy(identity = preview.identity.copy(ids = listOf(ExternalId("imdb", "tt1"))))
        val initial = LibraryMembershipSnapshot(listOf(MediaIdentity("tt1", "movie", emptyList())), 1, true)
        val saved = initial.copy(scoped_identities = listOf(ScopedMediaIdentity(verified.identity, origin)))
        var writes = 0
        val client = HttpClient(MockEngine { request ->
            assertNotEquals(HttpMethod.Delete, request.method, "An Add intent must not turn into canonical alias removal")
            val path = request.url.encodedPath
            val body = when {
                path.startsWith("/api/meta/") -> Json.encodeToString(DetailSnapshot(verified, episodes = emptyList()))
                request.method == HttpMethod.Put -> { writes++; "{}" }
                path.endsWith("/membership") -> Json.encodeToString(saved)
                else -> Json.encodeToString(CatalogPage(listOf(verified), null, true))
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store); controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "1", results = listOf(preview),
            favoriteKeys = setOf("movie" to "tt1"), libraryMembership = initial)
        try {
            assertFalse(controller.state.value.isSaved(preview))
            controller.toggleFavorite(preview); controller.job!!.join()
            assertNull(controller.state.value.error); assertEquals(1, writes)
            val shown = controller.state.value.results.single()
            assertEquals(verified.identity, shown.identity); assertEquals(preview.title, shown.title)
            assertTrue(controller.state.value.isSaved(shown))
        } finally { remote.close() }
    }
}
