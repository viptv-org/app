package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import org.viptv.engine.wire.*
import uniffi.viptv_core.SecretStore
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

class NativeSourceRankingTest {
    private val card = Card(MediaIdentity("tt1234567", "movie", emptyList()), provider_id = "metadata", title = "Fixture film", badges = emptyList())

    private class Fixture : AutoCloseable {
        val directory = Files.createTempDirectory("viptv-native-source-ranking-")
        val client = HttpClient(MockEngine { request ->
            val body = when (request.url.host) {
                "cam.example" -> """{"streams":[{"title":"Cam.1080p.mkv","quality_height":1080,"release_type":"cam","url":"https://media.example/cam.mp4"}]}"""
                else -> """{"streams":[{"title":"Clean.720p.mkv","quality_height":720,"url":"https://media.example/clean.mp4"}]}"""
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
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
            val ranked = fixture.local.rankSources("default", candidates)
            assertEquals(listOf(clean.id, cam.id), ranked.rows.map { it.id }, "The clean source ranks first; the camera release sorts last")
            assertEquals(SourceGroups(listOf(clean.id), clean.id), ranked.groups, "Only the clean source joins Best matches")
            assertTrue(ranked.bestMatchFullyMatches, "The clean direct 720p source matches the default preferences")
        }
    }
}