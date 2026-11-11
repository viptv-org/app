package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.*
import uniffi.viptv_core.SecretStore
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

class NativeSourceRankingTest {
    private val card = Card(MediaIdentity("tt1234567", "movie", emptyList()), provider_id = "metadata", title = "Fixture film", badges = emptyList())

    private class Fixture(private val body: (String) -> String = { host ->
        when (host) {
            "cam.example" -> """{"streams":[{"title":"Cam.1080p.mkv","quality_height":1080,"release_type":"cam","url":"https://media.example/cam.mp4"}]}"""
            else -> """{"streams":[{"title":"Clean.720p.mkv","quality_height":720,"url":"https://media.example/clean.mp4"}]}"""
        }
    }) : AutoCloseable {
        val directory = Files.createTempDirectory("viptv-native-source-ranking-")
        val requested = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requested += request.url.host
            respond(body(request.url.host), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val secrets = object : SecretStore {
            val values = ConcurrentHashMap<String, String>()
            override fun get(key: String) = values[key]
            override fun put(key: String, value: String) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
        }
        val local = NativeLocalConnection(directory.resolve("local.sqlite").toString(), secrets, client)

        init {
            listOf("cam.example", "good.example").forEach { host ->
                local.engine.installAddon("default", "https://$host/manifest.json",
                    """{"id":"$host","name":"$host","version":"1","resources":["stream"],"types":["movie"]}""")
            }
        }

        override fun close() { local.close(); directory.toFile().deleteRecursively() }
    }

    @Test fun localRankingReturnsBestMatchesAndTheFullyMatchesFlag() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val candidates = fixture.local.fetchLocalSources("default", card).candidates
            val cam = candidates.single { it.release_type == "cam" }
            val clean = candidates.single { it.release_type != "cam" }
            val ranked = fixture.local.rankSources("default", candidates, RankingContext())
            assertEquals(listOf(clean.id, cam.id), ranked.rows.map { it.id }, "The clean source ranks first; the camera release sorts last")
            assertEquals(SourceGroups(listOf(clean.id), clean.id), ranked.groups, "Only the clean source joins Best matches")
            assertTrue(ranked.bestMatchFullyMatches, "The clean direct 720p source matches the default preferences")
        }
    }

    @Test fun rememberedAddonStartsWithoutDispatchingOtherAddons() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val candidates = fixture.local.fetchLocalSources("default", card).candidates
            val clean = candidates.single { it.release_type != "cam" }
            fixture.requested.clear()
            val context = RankingContext(reuse = SourceReuseContext(title = SourceReuseMemory(clean.provider_id)))
            val outcome = fixture.local.discoverSources("default", card, context)
            assertEquals(listOf("good.example"), fixture.requested)
            assertEquals(listOf("Clean.720p.mkv"), outcome.candidates.map { it.label })
            assertTrue(fixture.local.rankSources("default", outcome.candidates, context).reuseReady)
        }
    }

    @Test fun unknownAudioExpandsDiscoveryWithoutReusingTheAttemptedAddon() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val candidates = fixture.local.fetchLocalSources("default", card).candidates
            val clean = candidates.single { it.release_type != "cam" }
            fixture.requested.clear()
            val context = RankingContext(reuse = SourceReuseContext(title = SourceReuseMemory(clean.provider_id, listOf("en"))))
            val outcome = fixture.local.discoverSources("default", card, context)
            assertEquals(listOf("good.example", "cam.example"), fixture.requested)
            assertFalse(fixture.local.rankSources("default", outcome.candidates, context).reuseReady)
        }
    }

    @Test fun anUnknownContextRanksExactlyLikeTheLegacyCall() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val candidates = fixture.local.fetchLocalSources("default", card).candidates
            val legacy = fixture.local.json.parseToJsonElement(fixture.local.engine.rankSources("default",
                fixture.local.json.encodeToString(candidates))).jsonObject
            assertEquals(rankedSourcesResponse(legacy, candidates),
                fixture.local.rankSources("default", candidates, RankingContext()))
        }
    }

    @Test fun anUnsupportedCodecLeavesTheBestMatches() = runBlocking<Unit> {
        val streams: (String) -> String = { host ->
            when (host) {
                "cam.example" -> """{"streams":[{"title":"Film.2020.2160p.HEVC.mkv","quality_height":2160,"url":"https://media.example/hevc.mp4"}]}"""
                else -> """{"streams":[{"title":"Film.2020.2160p.AVC.mkv","quality_height":2160,"url":"https://media.example/avc.mp4"}]}"""
            }
        }
        Fixture(streams).use { fixture ->
            val candidates = fixture.local.fetchLocalSources("default", card).candidates
            val hevc = candidates.single { it.video_codec == "hevc" }
            val avc = candidates.single { it.video_codec == "avc" }
            val context = RankingContext(device = DeviceCapability(videoCodecs = listOf(
                VideoCodecSupport("hevc", DecodeSupport.UNSUPPORTED),
                VideoCodecSupport("avc", DecodeSupport.SUPPORTED, 2160))))
            val ranked = fixture.local.rankSources("default", candidates, context)
            assertEquals(listOf(avc.id, hevc.id), ranked.rows.map { it.id }, "The undecodable codec sorts last")
            assertEquals(listOf(avc.id), ranked.groups.bestMatches)
        }
    }
}