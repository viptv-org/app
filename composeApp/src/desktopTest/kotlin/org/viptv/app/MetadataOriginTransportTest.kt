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

class MetadataOriginTransportTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val parent = MediaIdentity("opaque-parent", "series", emptyList())
    private val episode = Card(MediaIdentity("opaque-child", "series", emptyList(), EpisodeCoordinates("aired", 1, 1)),
        provider_id = "addon:7", title = "Episode", badges = emptyList(), parent_identity = parent)
    private val subtitles = """{"scope":{"connection_id":"remote","account_id":"1","profile_id":"1","revision":0},"candidates":[],"providers":[],"complete":true}"""

    @Test fun typedNextCarriesTheSelectedMetadataOriginAndRetainsReturnedOrigin() = runBlocking<Unit> {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/profiles/owner/continue/next", request.url.encodedPath)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("stremio", body["metadata_provider_id"]?.jsonPrimitive?.content)
            assertEquals(7, body["metadata_addon_id"]?.jsonPrimitive?.int)
            assertEquals("opaque-parent", body["parent_identity"]?.jsonObject?.get("id")?.jsonPrimitive?.content)
            respond(Json.encodeToString(NextEpisodeResult(ContinuationStatus.NEXT, episode.copy(title = "Next"))),
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try { assertEquals("addon:7", remote.nextEpisode("owner", episode).item?.provider_id) }
        finally { remote.close() }
    }

    @Test fun subtitleDiscoveryUsesOriginAndSafelyFallsBackForAnOlderServer() = runBlocking<Unit> {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            calls++
            if (calls == 1) {
                assertEquals("stremio", request.url.parameters["metadata_provider_id"])
                assertEquals("7", request.url.parameters["metadata_addon_id"])
                respond("{}", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                assertTrue(request.url.parameters.isEmpty())
                respond(subtitles, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try { assertTrue(remote.subtitles(episode).complete); assertEquals(2, calls) }
        finally { remote.close() }
    }

    @Test fun anOpaqueStremioSimklPrefixNeverFallsBackToAnotherMetadataProvider() = runBlocking<Unit> {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond("{}", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            assertEquals(422, assertFailsWith<RemoteFailure> { remote.subtitles(episode.copy(identity = episode.identity.copy(id = "simkl:not-a-real-simkl-id"))) }.status)
            assertEquals(1, calls)
        } finally { remote.close() }
    }

    @Test fun aTitleWithoutAnEpisodeHasNoFabricatedNextEpisode() = runBlocking<Unit> {
        val client = HttpClient(MockEngine { error("A title without coordinates must not issue a request") }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try { assertEquals(ContinuationStatus.UNAVAILABLE, remote.nextEpisode("owner", episode.copy(identity = parent, parent_identity = null)).status) }
        finally { remote.close() }
    }
    @Test fun modernOriginAndAssertionsReachDetailSourcesNextSubtitlesAndRemoval() = runBlocking<Unit> {
        val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
        val selected = episode.copy(metadata_origin = origin, identity = episode.identity.copy(ids = listOf(ExternalId("imdb", "tt123"))),
            poster = "https://images.example/private-presentation", description = "Presentation is omitted from selectors")
        val operations = mutableSetOf<String>()
        fun assertSelector(value: JsonElement?) {
            val selector = Json.decodeFromJsonElement<Card>(requireNotNull(value))
            assertEquals(origin, selector.metadata_origin)
            assertEquals(selected.identity, selector.identity)
            assertEquals(selected.parent_identity, selector.parent_identity)
            assertNull(selector.poster); assertNull(selector.description)
        }
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val body = when {
                path.startsWith("/api/meta/") -> {
                    operations += "detail"
                    assertEquals(origin, Json.decodeFromString<MetadataOrigin>(request.url.parameters["metadata_origin"]!!))
                    assertEquals("/api/meta/series/opaque-parent", path)
                    val expected = Json.decodeFromString<Card>(request.url.parameters["card"]!!)
                    assertEquals(origin, expected.metadata_origin); assertEquals(parent, expected.identity)
                    assertNull(expected.parent_identity); assertNull(expected.poster); assertNull(expected.description)
                    Json.encodeToString(DetailSnapshot(selected.copy(identity = parent, parent_identity = null), episodes = listOf(selected)))
                }
                path.startsWith("/api/subtitles/") -> {
                    operations += "subtitles"
                    assertEquals(origin, Json.decodeFromString<MetadataOrigin>(request.url.parameters["metadata_origin"]!!))
                    assertSelector(Json.parseToJsonElement(request.url.parameters["card"]!!)); subtitles
                }
                path.contains("/library/") -> {
                    operations += "remove"; assertEquals(HttpMethod.Delete, request.method)
                    assertSelector(Json.parseToJsonElement(request.url.parameters["card"]!!)); "{}"
                }
                path == "/api/streams" || path.endsWith("/continue/next") -> {
                    val value = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals(Json.encodeToJsonElement(origin), value["metadata_origin"])
                    assertSelector(value["card"])
                    if (path.endsWith("/continue/next")) { operations += "next"; "{\"status\":\"Unavailable\"}" }
                    else { operations += "sources"; "{\"id\":\"job\"}" }
                }
                else -> "{\"events\":[],\"done\":true}"
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            remote.detail(selected); remote.subtitles(selected); remote.sources(selected)
            remote.nextEpisode("owner", selected); remote.removeFavorite("owner", selected)
            assertEquals(setOf("detail", "subtitles", "sources", "next", "remove"), operations)
        } finally { remote.close() }
    }

    @Test fun aModernSubtitleOriginNeverDowngradesToAnOriginlessLookup() = runBlocking<Unit> {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++; respond("{}", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            val card = episode.copy(metadata_origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32)))
            assertEquals(422, assertFailsWith<RemoteFailure> { remote.subtitles(card) }.status)
            assertEquals(1, calls)
        } finally { remote.close() }
    }

}
