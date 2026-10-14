package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import uniffi.viptv_core.SecretStore
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.*

/** Real Rust/SQLite/planning/projection bridge with only external add-on IO replaced. */
class NativeDiscoverySelectionsTest {
    @Test fun explicitCatalogKeepsMultiValuesAndPagingInOneRequestWithoutMixingOwners() = runBlocking {
        val requests = mutableListOf<Pair<String, Map<String, List<String>>>>()
        val client = HttpClient(MockEngine { request ->
            val body = if (request.url.encodedPath.endsWith("manifest.json")) {
                val name = if (request.url.host == "first.example") "First" else "Second"
                """{"id":"${name.lowercase()}","name":"$name","version":"1.0.0","types":["movie"],"resources":["catalog"],"catalogs":[
                    {"id":"top","name":"Top","type":"movie","extra":[{"name":"genre","isRequired":true,"options":["Sci-Fi & Fantasy","Drama/Comedy","Horror"],"optionsLimit":2},{"name":"skip"}]},
                    {"id":"search","name":"Search","type":"movie","extra":[{"name":"search","isRequired":true}]}
                ]}"""
            } else {
                val pairs = request.url.encodedPath.substringAfterLast('/').removeSuffix(".json").split('&').map { extra ->
                    val pieces = extra.split('=', limit = 2)
                    URLDecoder.decode(pieces[0], StandardCharsets.UTF_8) to URLDecoder.decode(pieces[1], StandardCharsets.UTF_8)
                }.groupBy({ it.first }, { it.second })
                requests += request.url.host to pairs
                """{"metas":[{"id":"tt123456","type":"movie","name":"Fixture movie"}]}"""
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val directory = Files.createTempDirectory("viptv-discovery-selections-")
        val secrets = object : SecretStore {
            private val values = mutableMapOf<String, String>()
            override fun get(key: String) = values[key]
            override fun put(key: String, value: String) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
        }
        val local = NativeLocalConnection(directory.resolve("local.sqlite").toString(), secrets, client)
        try {
            local.installAddon("default", "https://first.example/manifest.json")
            local.installAddon("default", "https://second.example/manifest.json")
            val controls = local.discoveryControls("default", "movie")
            val selected = controls.catalogs.single { it.connection_name == "Second" && it.id == "top" }
            val genre = selected.controls.single { it.id == "genre" }
            assertEquals(ControlKind.MULTICHOICE, genre.kind); assertTrue(genre.required)
            assertEquals(2L, genre.maximum_selections)
            val values = listOf("Sci-Fi & Fantasy", "Drama/Comedy")
            val query = BrowseQuery("movie", selected.id, selected.connection_id, values = listOf(QueryValue("genre", values)))
            val page = local.browse("default", query)
            assertEquals("Fixture movie", page.cards.single().title)
            val next = requireNotNull(page.next_cursor).toInt()
            local.browse("default", query.copy(skip = next))
            assertEquals(2, requests.size); assertTrue(requests.all { it.first == "second.example" })
            assertTrue(requests.all { it.second["genre"] == values })
            assertEquals(listOf(next.toString()), requests[1].second["skip"])
            assertFails { local.browse("default", query.withSelections("genre", listOf("Not advertised"))) }
            assertEquals(2, requests.size, "Invalid choices must fail before provider IO")
            val search = controls.catalogs.single { it.id == "search" && it.connection_id == selected.connection_id }
            local.browse("default", query.forCatalog(search).copy(search = "Naruto"))
            assertEquals(3, requests.size); assertEquals("second.example", requests.last().first)
            assertEquals(listOf("Naruto"), requests.last().second["search"])
            assertNull(requests.last().second["genre"], "The prior catalog's genres must not enter a search-only catalog")
            val controller = ApplicationController(this)
            controller.local = local
            controller.mutable.value = ApplicationState(screen = Screen.Discover, selectedProfile = "default", localMode = true,
                browseQuery = query, browseControls = controls, browseCards = page.cards, browseNext = page.next_cursor)
            controller.loadAddons()
            assertNull(controller.state.value.browseControls); assertNull(controller.state.value.browseNext)
            assertTrue(controller.state.value.browseCards.orEmpty().isEmpty())
            assertEquals(3, requests.size, "Refreshing add-on configuration must not request a catalog")
        } finally { local.close(); directory.toFile().deleteRecursively() }
    }
}
