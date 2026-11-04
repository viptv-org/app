package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class BrowsePagingFooterTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun card(number: Int) = Card(identity = MediaIdentity("tt$number", "movie", emptyList()),
        provider_id = "stremio", title = "Movie $number", badges = emptyList())

    private fun controls() = DiscoveryControls(
        provider_id = "stremio", media_types = listOf("movie"), controls = emptyList(),
        catalogs = listOf(DiscoveryCatalog("popular", "Popular", "movie", connection_id = null, addon_id = null,
            controls = emptyList())),
        discovery = FeatureAvailability("Available"), search = FeatureAvailability("Available"),
        filters = FeatureAvailability("Available"), calendar = FeatureAvailability("Available"))

    private suspend fun await(what: String, predicate: () -> Boolean) {
        withTimeout(5_000) { while (!predicate()) delay(10) }
    }

    @Test fun aFailedPageKeepsCardsAndRetryRecovers() = runBlocking {
        val requested = mutableListOf<Int>()
        var failNext = false
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/discovery-controls" ->
                    respond(Json.encodeToString(controls()), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                "/api/discover" -> {
                    if (failNext) { failNext = false; respondError(HttpStatusCode.InternalServerError) }
                    else {
                        val skip = requireNotNull(request.url.parameters["skip"]).toInt()
                        requested += skip
                        val total = 6
                        val end = minOf(skip + 2, total)
                        respond(Json.encodeToString(CatalogPage(cards = ((skip + 1)..end).map(::card),
                            next_cursor = end.takeIf { it < total }?.toString(), complete = end >= total)),
                            headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Discover, selectedProfile = "owner", browseQuery = BrowseQuery("series"))
        try {
            controller.applyBrowse(BrowseQuery("movie"))
            await("first page") { controller.state.value.browseCards.isNotEmpty() && !controller.state.value.busy }
            assertEquals(2, controller.state.value.browseCards.size)
            assertEquals("2", controller.state.value.browseNext)
            assertNull(controller.state.value.browsePageError)

            failNext = true
            controller.browseMore()
            await("failed page") { controller.state.value.browsePageError != null && !controller.state.value.busy }
            assertEquals(2, controller.state.value.browseCards.size)
            assertEquals("2", controller.state.value.browseNext)
            assertNull(controller.state.value.error)

            controller.browseMore()
            await("retried") { controller.state.value.browsePageError == null && controller.state.value.browseCards.size == 4 }
            assertEquals(listOf(0, 2), requested)
        } finally { remote.close() }
    }
}