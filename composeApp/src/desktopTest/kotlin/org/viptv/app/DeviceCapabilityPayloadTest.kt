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

class DeviceCapabilityPayloadTest {
    private val candidates = listOf(SourceCandidate("a", "addon:1", "A"), SourceCandidate("b", "addon:2", "B"))

    private fun JsonObjectBuilder.ids() = put("ids", JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))))

    @Test fun fullCapabilitySerializesToTheSnakeCaseContract() {
        val context = RankingContext(
            device = DeviceCapability(
                maxHeight = 1080,
                videoCodecs = listOf(VideoCodecSupport("hevc", DecodeSupport.SUPPORTED, 2160)),
                dolbyVision = DecodeSupport.UNSUPPORTED,
                displayHdr = listOf(HdrSupport("hdr10", DecodeSupport.SUPPORTED)),
                audioCodecs = listOf(AudioCodecSupport("eac3", DecodeSupport.SUPPORTED)),
            ),
            seriesMemory = SeriesMemory("addon:1", "binge-group-1"),
        )
        assertEquals(buildJsonObject {
            put("device", buildJsonObject {
                put("max_height", 1080)
                putJsonArray("video_codecs") { addJsonObject {
                    put("codec", "hevc"); put("support", "supported"); put("max_height", 2160)
                } }
                put("dolby_vision", "unsupported")
                putJsonArray("display_hdr") { addJsonObject { put("hdr", "hdr10"); put("support", "supported") } }
                putJsonArray("audio_codecs") { addJsonObject { put("codec", "eac3"); put("support", "supported") } }
            })
            put("series_memory", buildJsonObject { put("provider_id", "addon:1"); put("binge_group", "binge-group-1") })
        }, rankingContextJson(context))
        assertEquals(buildJsonObject {
            ids()
            put("device_max_height", 1080)
            put("device", buildJsonObject { put("max_height", 1080) })
        }, sourceRankingRequest(candidates, RankingContext(device = DeviceCapability(maxHeight = 1080))))
    }

    @Test fun unknownAnswersAreOmitted() {
        assertEquals(buildJsonObject {}, rankingContextJson(RankingContext()))
        assertEquals(buildJsonObject {}, rankingContextJson(RankingContext(device = DeviceCapability(
            videoCodecs = listOf(VideoCodecSupport("hevc", DecodeSupport.UNKNOWN)),
            displayHdr = listOf(HdrSupport("hdr10", DecodeSupport.UNKNOWN)),
            audioCodecs = listOf(AudioCodecSupport("eac3", DecodeSupport.UNKNOWN)),
        ))))
        assertEquals(buildJsonObject { ids() }, sourceRankingRequest(candidates, RankingContext()))
    }

    @Test fun invalidEntriesAreDroppedAndListsAreCapped() {
        val context = RankingContext(device = DeviceCapability(
            maxHeight = 5000,
            videoCodecs = listOf(
                VideoCodecSupport("hevc", DecodeSupport.SUPPORTED, 100),
                VideoCodecSupport("this-name-is-too-long", DecodeSupport.SUPPORTED),
                VideoCodecSupport("bad name", DecodeSupport.SUPPORTED),
            ) + (1..9).map { VideoCodecSupport("c$it", DecodeSupport.SUPPORTED) },
        ))
        val device = rankingContextJson(context).getValue("device").jsonObject
        assertFalse("max_height" in device)
        val codecs = device.getValue("video_codecs").jsonArray.map { it.jsonObject.getValue("codec").jsonPrimitive.content }
        assertEquals(8, codecs.size)
        assertEquals(listOf("hevc", "c1", "c2", "c3", "c4", "c5", "c6", "c7"), codecs)
        assertFalse("max_height" in device.getValue("video_codecs").jsonArray.first().jsonObject)
    }

    @Test fun seriesMemoryIsOnlySentWhenItValidates() {
        assertEquals(buildJsonObject {}, rankingContextJson(RankingContext(seriesMemory = SeriesMemory("", "group"))))
        assertEquals(buildJsonObject {}, rankingContextJson(RankingContext(seriesMemory = SeriesMemory("addon:1", " "))))
        assertEquals(buildJsonObject {}, rankingContextJson(RankingContext(seriesMemory = SeriesMemory("addon:1", "x".repeat(129)))))
        assertEquals(buildJsonObject {}, rankingContextJson(RankingContext(seriesMemory = SeriesMemory("addon:1", "bad\u0000group"))))
        assertEquals(buildJsonObject { put("series_memory", buildJsonObject {
            put("provider_id", "addon:1"); put("binge_group", "x".repeat(128))
        }) }, rankingContextJson(RankingContext(seriesMemory = SeriesMemory("addon:1", "x".repeat(128)))))
    }

    @Test fun remoteRequestCarriesTheContextAndKeepsTheOlderKeys() = runBlocking {
        val bodies = EngineRequests<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            bodies += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond("""{"streams":[{"id":"b"},{"id":"a"}]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val context = RankingContext(
            device = DeviceCapability(maxHeight = 2160, videoCodecs = listOf(VideoCodecSupport("avc", DecodeSupport.SUPPORTED, 2160))),
            seriesMemory = SeriesMemory("addon:1", "group"),
        )
        try {
            remote.rankSources("7", candidates, context)
            remote.rankSources("7", candidates)
        } finally { remote.close() }
        assertEquals(listOf("a", "b"), bodies[0].getValue("ids").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(2160, bodies[0].getValue("device_max_height").jsonPrimitive.int)
        assertEquals("avc", bodies[0].getValue("device").jsonObject.getValue("video_codecs").jsonArray.first()
            .jsonObject.getValue("codec").jsonPrimitive.content)
        assertEquals("addon:1", bodies[0].getValue("series_memory").jsonObject.getValue("provider_id").jsonPrimitive.content)
        assertFalse("device_max_height" in bodies[1])
        assertFalse("device" in bodies[1])
        assertFalse("series_memory" in bodies[1])
    }
}
