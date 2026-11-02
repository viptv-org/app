package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.*
import kotlin.test.*

class SourceRequestFieldsTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun requestFor(card: Card): JsonObject = runBlocking {
        var body: JsonObject? = null
        val client = HttpClient(MockEngine { request ->
            val response = when {
                request.method == HttpMethod.Post && request.url.encodedPath == "/api/streams" -> {
                    body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    """{"id":"job"}"""
                }
                else -> """{"events":[],"done":true}"""
            }
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try { remote.sources(card) } finally { remote.close() }
        requireNotNull(body)
    }

    @Test fun movieWithOnlyTitleAndYearSendsBothForTheProviderMatch() {
        val card = Card(MediaIdentity("addon:1:tt0111161", "movie", emptyList()), provider_id = "addon:7",
            title = "The Shawshank Redemption", badges = emptyList(), metadata = CardMetadata(year = 1994))
        val body = requestFor(card)
        assertEquals("movie", body.getValue("type").jsonPrimitive.content)
        assertEquals("addon:1:tt0111161", body.getValue("id").jsonPrimitive.content)
        assertEquals("The Shawshank Redemption", body.getValue("name").jsonPrimitive.content)
        assertEquals(1994L, body.getValue("year").jsonPrimitive.long)
        assertEquals(setOf("type", "id", "name", "year", "metadata_provider_id", "metadata_addon_id"), body.keys)
    }

    @Test fun movieWithFullIdsSendsEveryAcceptedIdentity() {
        val card = Card(MediaIdentity("tmdb:603", "movie", listOf(ExternalId("imdb", "tt0133093"), ExternalId("tmdb", "603"))),
            provider_id = "simkl", title = "The Matrix", badges = emptyList(), metadata = CardMetadata(year = 1999))
        val body = requestFor(card)
        assertEquals("tt0133093", body.getValue("imdb_id").jsonPrimitive.content)
        assertEquals("603", body.getValue("tmdb_id").jsonPrimitive.content)
        assertEquals("The Matrix", body.getValue("name").jsonPrimitive.content)
        assertEquals(1999L, body.getValue("year").jsonPrimitive.long)
    }

    @Test fun episodeSendsTheShowIdentityAndItsOwnCoordinatesButNoEpisodeTitle() {
        val show = MediaIdentity("tt0903747", "series", listOf(ExternalId("imdb", "tt0903747"), ExternalId("tmdb", "1396")))
        val episode = Card(MediaIdentity("tt0903747:5:14", "series", emptyList(), EpisodeCoordinates("aired", 5, 14)),
            provider_id = "simkl", title = "Ozymandias", badges = emptyList(), parent_identity = show)
        val body = requestFor(episode)
        assertEquals("tt0903747", body.getValue("imdb_id").jsonPrimitive.content)
        assertEquals("1396", body.getValue("tmdb_id").jsonPrimitive.content)
        assertEquals("tt0903747", body.getValue("series_id").jsonPrimitive.content)
        assertEquals(5L, body.getValue("season").jsonPrimitive.long)
        assertEquals(14L, body.getValue("episode").jsonPrimitive.long)
        assertFalse("name" in body)
        assertFalse("year" in body)
    }

    @Test fun valuesTheHostWouldRejectAreLeftOut() {
        val card = Card(MediaIdentity("addon:1:x", "movie", listOf(ExternalId("imdb", "IMDB 1"), ExternalId("tmdb", "tmdb:abc"))),
            provider_id = "simkl", title = "  ", badges = emptyList(), metadata = CardMetadata(year = 12))
        val body = requestFor(card)
        assertFalse("imdb_id" in body)
        assertFalse("tmdb_id" in body)
        assertFalse("name" in body)
        assertFalse("year" in body)
        assertEquals(setOf("type", "id", "metadata_provider_id"), body.keys)
    }
}
