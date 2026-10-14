package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.QueryValue
import kotlin.test.*

class DiscoverySelectionsTest {
    private fun controls() = DiscoveryControls("stremio", listOf("movie"), emptyList(), catalogs = listOf(
        DiscoveryCatalog("top", "Top", "movie", "addon:1", 1, connection_name = "First add-on"),
        DiscoveryCatalog("top", "Top", "movie", "addon:2", 2, connection_name = "Second add-on")),
        discovery = FeatureAvailability("Available"), search = FeatureAvailability("Available"),
        filters = FeatureAvailability("Available"), calendar = FeatureAvailability("Unsupported"))

    @Test fun duplicateCatalogNamesKeepTheirOwnersAndDefaultBrowsingUsesOneCatalog() {
        val controls = controls(); val second = controls.catalogs[1]
        assertNull(controls.catalogSelection("top"))
        assertEquals(second, controls.catalogSelection(second.selectionKey()))
        assertEquals("Top · Second add-on", second.displayName(controls.catalogs))
        val sameName = second.copy(connection_name = "First add-on")
        assertEquals("Top · First add-on (2)", sameName.displayName(listOf(controls.catalogs[0], sameName)))
        val initial = BrowseQuery("movie", genre = "Drama").withDefaultCatalog(controls)
        assertEquals("addon:1", initial.connection_id); assertEquals("top", initial.catalog)
        assertEquals("Drama", initial.genre)
        val switched = initial.forCatalog(second)
        assertEquals("addon:2", switched.connection_id); assertEquals("top", switched.catalog)
        assertNull(switched.genre)
    }

    @Test fun catalogSwitchDoesNotSendPreviousCatalogFilters() {
        val prior = BrowseQuery("movie", "first", "addon:1", skip = 96, search = "Old search",
            genre = "Drama", extras = mapOf("language" to "fr"),
            values = listOf(QueryValue("country", listOf("France", "Canada"))))
        assertSame(prior, prior.forCatalog(DiscoveryCatalog("first", "First", "movie", connection_id = "addon:1")))
        val next = prior.forCatalog(DiscoveryCatalog("second", "Second", "movie", connection_id = "addon:2"))
        assertEquals("movie", next.type); assertEquals("second", next.catalog); assertEquals("addon:2", next.connection_id)
        assertEquals(0, next.skip); assertNull(next.genre); assertNull(next.search)
        assertTrue(next.extras.isEmpty()); assertTrue(next.values.isEmpty())
        val selected = prior.withSelections("genre", listOf("Action", "Drama"))
        assertNull(selected.genre); assertEquals(listOf("Action", "Drama"), selected.selections("genre"))
        assertEquals(listOf("France", "Canada"), selected.selections("country"))
    }

    @Test fun remoteMultiValuesStayInOneJsonParameterAcrossPagesWhileScalarWireStaysCompatible() = runBlocking {
        val selection = listOf(QueryValue("genre", listOf("Sci-Fi & Fantasy", "Drama/Comedy")))
        val skips = mutableListOf<Int>()
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val client = HttpClient(MockEngine { request ->
            val skip = request.url.parameters["skip"]!!.toInt(); skips += skip
            if (skip < 192) {
                assertEquals("2", request.url.parameters["addon_id"])
                assertEquals(1, request.url.parameters.getAll("values")?.size)
                assertEquals(Json.encodeToString(selection), request.url.parameters["values"])
                assertNull(request.url.parameters["genre"])
            } else {
                assertNull(request.url.parameters["values"])
                assertEquals("Drama", request.url.parameters["genre"])
                assertEquals("language", Json.parseToJsonElement(request.url.parameters["extras"]!!).jsonObject.keys.single())
            }
            respond("""{"cards":[],"next_cursor":null,"complete":true}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            val query = BrowseQuery("movie", catalog = "top", connection_id = "addon:2", values = selection)
            remote.browse(query, controls()); remote.browse(query.copy(skip = 96), controls())
            remote.browse(BrowseQuery("movie", skip = 192, genre = "Drama", extras = mapOf("language" to "en")), null)
            assertFalse(Json.encodeToString(BrowseQuery("movie")).contains("values"))
        } finally { remote.close() }
        assertEquals(listOf(0, 96, 192), skips)
    }
}
