package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class BrowseTimeframeTest {
    @Test fun selectingAGenreReplacesAStaleTimeframeWithTheAdvertisedValue() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/discovery-controls" -> {
                    assertEquals("drama", request.url.parameters["genre"])
                    respond("""{"provider_id":"simkl","media_types":["movie"],"controls":[{"id":"timeframe","label":"Trending","kind":"SingleChoice","required":false,"choices":[{"value":"month","label":"This month"}]}],"discovery":{"availability":"Available"},"search":{"availability":"Available"},"filters":{"availability":"Available"},"calendar":{"availability":"Available"}}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                "/api/discover" -> {
                    assertEquals("drama", request.url.parameters["genre"])
                    assertEquals("{\"timeframe\":\"month\"}", request.url.parameters["extras"])
                    respond("""{"cards":[],"complete":true}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> error("Unexpected request")
            }
        }) { configureTransport() }
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Discover, selectedProfile = "owner")
        try {
            controller.loadBrowse(BrowseQuery("movie", genre = "drama", extras = mapOf("timeframe" to "week")))
            assertEquals("month", controller.state.value.browseQuery.extras["timeframe"])
        } finally { remote.close() }
    }
}
