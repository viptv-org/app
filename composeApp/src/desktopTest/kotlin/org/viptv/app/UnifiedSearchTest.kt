package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import io.ktor.http.content.TextContent
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class UnifiedSearchTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private fun card(provider: String) = Card(MediaIdentity("tt123", "series", emptyList()),
        provider_id = provider, title = "Same title", badges = emptyList())

    @Test fun unifiedSearchKeepsDuplicateTitlesInTheirProviderRowsAndRetainsFailures() = runBlocking {
        val expected = SearchSnapshot(listOf(
            SearchResultRow("simkl:tv", "TV", "simkl", cards = listOf(card("simkl"))),
            SearchResultRow("addon:42", "Community catalog", "stremio", 42, cards = listOf(card("addon:42"))),
            SearchResultRow("addon:43", "Unavailable catalog", "stremio", 43, "Failed", message = "Search unavailable")), false)
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/search", request.url.encodedPath)
            assertEquals("Space & Time", request.url.parameters["query"])
            respond(Json.encodeToString(expected), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "profile")
        try {
            controller.search("Space & Time"); controller.job!!.join()
            assertEquals(expected.rows, controller.state.value.searchRows)
            assertEquals(listOf("simkl", "addon:42"), controller.state.value.results.map { it.provider_id })
            assertNull(controller.state.value.error)
        } finally { remote.close() }
    }

    @Test fun deletingWithoutChangingTheSearchTermDoesNotSearchAgain() = runBlocking {
        var requests = 0
        val expected = SearchSnapshot(emptyList(), false)
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals("/api/search", request.url.encodedPath)
            assertEquals("Space & Time", request.url.parameters["query"])
            respond(Json.encodeToString(expected), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "profile")
        try {
            controller.search("Space & Time"); controller.job!!.join()
            controller.search("Space & Time")
            yield()
            assertEquals(1, requests)
            assertEquals("Space & Time", controller.state.value.searchTerm)
        } finally { remote.close() }
    }

    @Test fun detailPaginationKeepsTheSelectedSearchAddonInsteadOfTheDefaultBrowser() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            assertEquals("stremio", request.url.parameters["provider_id"])
            assertEquals("42", request.url.parameters["addon_id"])
            assertEquals("24", request.url.parameters["offset"])
            assertEquals("96", request.url.parameters["limit"])
            respond(Json.encodeToString(DetailSnapshot(card("addon:42"), episodes = emptyList())),
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try { assertEquals("addon:42", remote.detail(card("addon:42"), 24, 96).item.provider_id) }
        finally { remote.close() }
    }

    @Test fun olderServerWithoutGroupedSearchUsesItsExistingSearchRoute() = runBlocking {
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            if (request.url.encodedPath == "/api/search") respond("{}", HttpStatusCode.NotFound,
                headersOf(HttpHeaders.ContentType, "application/json"))
            else respond(Json.encodeToString(CatalogPage(listOf(card("stremio")), null, true)),
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "profile")
        try {
            controller.search("Same"); controller.job!!.join()
            assertEquals(listOf("/api/search", "/api/discover"), paths)
            assertEquals(1, controller.state.value.results.size)
            assertTrue(controller.state.value.searchRows.isEmpty())
        } finally { remote.close() }
    }
    @Test fun sourceDiscoveryCarriesTheSelectedMetadataOriginAlongWithTheMediaIdentity() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            val body = if (request.method == HttpMethod.Post) {
                val value = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals("stremio", value["metadata_provider_id"]?.jsonPrimitive?.content)
                assertEquals(42L, value["metadata_addon_id"]?.jsonPrimitive?.long)
                assertEquals("tt123", value["id"]?.jsonPrimitive?.content)
                assertEquals("parent-title", value["series_id"]?.jsonPrimitive?.content)
                """{"id":"job"}"""
            } else """{"events":[],"done":true}"""
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try { assertTrue(remote.sources(card("addon:42").copy(parent_identity = MediaIdentity("parent-title", "series", emptyList()))).isEmpty()) }
        finally { remote.close() }
    }

}
