package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.features.sources.*
import org.viptv.engine.wire.*
import kotlin.test.*

class ReleaseFactsTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val card = Card(MediaIdentity("title", "movie", emptyList()), provider_id = "simkl", title = "Title", badges = emptyList())

    private fun streams(vararg streams: String): List<SourceCandidate> = runBlocking {
        val client = HttpClient(MockEngine { request ->
            val body = if (request.method == HttpMethod.Post) """{"id":"job"}""" else
                """{"events":[{"source":"addon:1","streams":[${streams.joinToString(",")}]}],"done":true}"""
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try { remote.sources(card).candidates } finally { remote.close() }
    }

    @Test fun serverCardsCarryReleaseFactsAndOlderCardsStayUnknown() {
        val (full, old, odd) = streams(
            """{"id":"a","name":"A","quality_height":2160,"release_type":"remux","three_d":true,"dynamic_range":["dv","hdr10"],
                "audio_flags":["dual"],"seeders":42,"delivery":"torrent"}""",
            """{"id":"b","name":"B","quality_height":1080}""",
            """{"id":"c","name":"C","release_type":"unknown","three_d":null,"dynamic_range":null,"audio_flags":"dual","seeders":"12","delivery":null}""")
        assertEquals("remux", full.release_type); assertTrue(full.three_d)
        assertEquals(listOf("dv", "hdr10"), full.dynamic_range); assertEquals(listOf("dual"), full.audio_flags)
        assertEquals(42L, full.seeders); assertEquals("torrent", full.delivery)
        assertEquals(SourceCandidate("b", "addon:1", "B", quality_height = 1080), old.copy(detail = null))
        assertEquals(SourceCandidate("c", "addon:1", "C"), odd.copy(detail = null))
    }

    @Test fun serializedCandidatesDecodeWithAndWithoutFacts() {
        val json = Json { ignoreUnknownKeys = true }
        val bare = json.decodeFromString<SourceCandidate>("""{"id":"a","provider_id":"p","label":"A","audio_languages":["en"]}""")
        assertNull(bare.release_type); assertFalse(bare.three_d); assertTrue(bare.dynamic_range.isEmpty()); assertNull(bare.seeders)
        val full = json.decodeFromString<SourceCandidate>("""{"id":"a","provider_id":"p","label":"A","release_type":"web_dl",
            "three_d":false,"dynamic_range":["hdr10_plus"],"audio_flags":["multi","dubbed"],"seeders":null,"delivery":"direct","dv_base_layer":"hdr10"}""")
        assertEquals(listOf("Dubbed", "Multi audio", "HDR"), full.sourceTags())
        assertEquals("direct", full.delivery)
    }

    @Test fun theRowShowsOnlyTheFixedTagSet() {
        val base = SourceCandidate("a", "p", "A")
        assertEquals(emptyList(), base.sourceTags())
        assertEquals(listOf("Dubbed", "Multi audio"), base.copy(audio_flags = listOf("multi", "dubbed")).sourceTags())
        assertEquals(listOf("HDR", "Dolby Vision", "Remux", "3D"),
            base.copy(dynamic_range = listOf("dv", "hdr10_plus", "hdr10"), release_type = "remux", three_d = true).sourceTags())
        assertEquals(listOf("CAM"), base.copy(release_type = "cam").sourceTags())
        assertEquals(emptyList(), base.copy(release_type = "bluray", audio_languages = listOf("ja"), subbed = true, subtitle_languages = listOf("en")).sourceTags(),
            "languages and release types are not tags")
    }

    @Test fun displayHeightsSnapToVideoTiers() {
        assertEquals(2160, snapVideoHeight(4320)); assertEquals(2160, snapVideoHeight(2160))
        assertEquals(1440, snapVideoHeight(1600)); assertEquals(1440, snapVideoHeight(1440))
        assertEquals(1080, snapVideoHeight(1200)); assertEquals(1080, snapVideoHeight(1080))
        assertEquals(720, snapVideoHeight(1079)); assertEquals(720, snapVideoHeight(720))
        assertEquals(480, snapVideoHeight(719)); assertEquals(480, snapVideoHeight(240))
    }

    @Test fun rankingRequestSendsTheDeviceHeightOnlyWhenKnown() = runBlocking {
        val candidates = listOf(SourceCandidate("a", "p", "A"), SourceCandidate("b", "p", "B"))
        val heights = RankingContext(device = DeviceCapability(maxHeight = 1080))
        assertEquals(buildJsonObject {
            put("ids", JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))))
            put("device_max_height", 1080)
            put("device", buildJsonObject { put("max_height", 1080) })
        }, sourceRankingRequest(candidates, heights))
        assertFalse("device_max_height" in sourceRankingRequest(candidates, RankingContext()))
        assertFalse("device_max_height" in sourceRankingRequest(candidates, RankingContext(device = DeviceCapability(maxHeight = 100))))
        val bodies = EngineRequests<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/profiles/7/source-ranking", request.url.encodedPath)
            bodies += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond("""{"streams":[{"id":"b"},{"id":"a"}]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            assertEquals(listOf("b", "a"), remote.rankSources("7", candidates, RankingContext(device = DeviceCapability(maxHeight = 2160))).rows.map { it.id })
            remote.rankSources("7", candidates)
        } finally { remote.close() }
        assertEquals(2160, bodies[0].getValue("device_max_height").jsonPrimitive.int)
        assertFalse("device_max_height" in bodies[1])
        assertFalse("device" in bodies[1])
    }
}
