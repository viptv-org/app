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

/** SRC-BEST-MATCH-001 phase 1: release facts on source cards and the device height sent to ranking. */
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
        try { remote.sources(card) } finally { remote.close() }
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
        // Malformed or "unknown" values are unknown, not errors.
        assertEquals(SourceCandidate("c", "addon:1", "C"), odd.copy(detail = null))
    }

    @Test fun serializedCandidatesDecodeWithAndWithoutFacts() {
        val json = Json { ignoreUnknownKeys = true }
        val bare = json.decodeFromString<SourceCandidate>("""{"id":"a","provider_id":"p","label":"A","audio_languages":["en"]}""")
        assertNull(bare.release_type); assertFalse(bare.three_d); assertTrue(bare.dynamic_range.isEmpty()); assertNull(bare.seeders)
        val full = json.decodeFromString<SourceCandidate>("""{"id":"a","provider_id":"p","label":"A","release_type":"web_dl",
            "three_d":false,"dynamic_range":["hdr10_plus"],"audio_flags":["multi","dubbed"],"seeders":null,"delivery":"direct","dv_base_layer":"hdr10"}""")
        assertEquals(listOf("WEB-DL", "HDR10+", "Multi audio"), full.factBadges())
        assertEquals("direct", full.delivery)
    }

    @Test fun badgesFollowTheSpecOrderAndPrecedence() {
        val base = SourceCandidate("a", "p", "A")
        assertEquals(emptyList(), base.factBadges())
        mapOf("web_dl" to "WEB-DL", "webrip" to "WEBRip", "bluray" to "BluRay", "remux" to "Remux", "hdtv" to "HDTV",
            "dvd" to "DVD", "other_rip" to "Rip", "cam" to "CAM").forEach { (value, label) ->
            assertEquals(listOf(label), base.copy(release_type = value).factBadges())
        }
        assertEquals(emptyList(), base.copy(release_type = "unknown").factBadges())
        assertEquals(listOf("DV"), base.copy(dynamic_range = listOf("hdr10", "hdr10_plus", "dv")).factBadges())
        assertEquals(listOf("HDR10+"), base.copy(dynamic_range = listOf("hdr10", "hdr10_plus")).factBadges())
        assertEquals(listOf("HDR"), base.copy(dynamic_range = listOf("hdr10")).factBadges())
        assertEquals(listOf("HDR"), base.copy(dynamic_range = listOf("hlg")).factBadges())
        assertEquals(listOf("Dual audio"), base.copy(audio_flags = listOf("dubbed", "multi", "dual")).factBadges())
        assertEquals(listOf("Multi audio"), base.copy(audio_flags = listOf("dubbed", "multi")).factBadges())
        assertEquals(listOf("Dubbed"), base.copy(audio_flags = listOf("dubbed")).factBadges())
        assertEquals(listOf("BluRay", "DV", "Dubbed", "3D"),
            base.copy(release_type = "bluray", dynamic_range = listOf("dv"), audio_flags = listOf("dubbed"), three_d = true).factBadges())
    }

    @Test fun narrowRowsDropTrailingBadgesWhole() {
        assertEquals(listOf(0, 1, 2), sourceFactBadgesThatFit(listOf(40, 30, 50), 4, 128))
        assertEquals(listOf(0, 1), sourceFactBadgesThatFit(listOf(40, 30, 50), 4, 127))
        // A later, narrower badge never jumps ahead of a dropped one.
        assertEquals(listOf(0), sourceFactBadgesThatFit(listOf(40, 90, 10), 4, 60))
        assertEquals(emptyList(), sourceFactBadgesThatFit(listOf(80), 4, 60))
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
        assertEquals(buildJsonObject { put("ids", JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b")))); put("device_max_height", 1080) },
            sourceRankingRequest(candidates, 1080))
        assertFalse("device_max_height" in sourceRankingRequest(candidates, null))
        assertFalse("device_max_height" in sourceRankingRequest(candidates, 100))
        val bodies = mutableListOf<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/profiles/7/source-ranking", request.url.encodedPath)
            bodies += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond("""{"streams":[{"id":"b"},{"id":"a"}]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            assertEquals(listOf("b", "a"), remote.rankSources("7", candidates, 2160).rows.map { it.id })
            remote.rankSources("7", candidates, null)
        } finally { remote.close() }
        assertEquals(2160, bodies[0].getValue("device_max_height").jsonPrimitive.int)
        assertFalse("device_max_height" in bodies[1])
    }
}
