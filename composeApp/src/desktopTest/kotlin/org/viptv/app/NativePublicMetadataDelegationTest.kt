package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.*
import uniffi.viptv_core.SecretStore
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.*

/** Actual Rust planning/proof/grants; only add-on HTTP is replaced. */
class NativePublicMetadataDelegationTest {
    private class Fixture : AutoCloseable {
        val directory = Files.createTempDirectory("viptv-native-public-metadata-")
        val requests = mutableListOf<String>()
        var holdMetadata = false
        val metadataStarted = CompletableDeferred<Unit>()
        val releaseMetadata = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            val host = request.url.host; val route = request.url.encodedPath
            synchronized(requests) { requests += "$host$route" }
            val body = when {
                host == "search.example" && "/catalog/" in route -> """{"metas":[{"id":"tt1111111","type":"movie","name":"Search result"}]}"""
                host == "metadata.example" && "/meta/" in route -> {
                    if (holdMetadata) { metadataStarted.complete(Unit); releaseMetadata.await() }
                    val series = "/series/" in route
                    buildJsonObject { put("meta", buildJsonObject {
                        put("id", "tt1111111"); put("type", if (series) "series" else "movie"); put("name", "Verified metadata"); put("tmdb_id", 42)
                        if (series) put("videos", JsonArray((1..1500).map { number -> buildJsonObject {
                            put("id", "tt1111111:1:$number"); put("season", 1); put("episode", number); put("title", "Episode $number")
                        } }))
                    }) }.toString()
                }
                host == "metadata.example" && "/stream/" in route -> """{"streams":[{"name":"1080p","url":"https://media.example/delegated.mp4"}]}"""
                host == "metadata.example" && "/subtitles/" in route -> """{"subtitles":[{"id":"one","lang":"en","url":"https://captions.example/delegated.vtt"}]}"""
                host == "captions.example" -> "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nDelegated caption\n"
                else -> error("Unexpected request $host$route")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, if (host == "captions.example") "text/vtt" else "application/json"))
        }) { configureTransport() }
        val secrets = object : SecretStore {
            val values = ConcurrentHashMap<String, String>()
            override fun get(key: String) = values[key]
            override fun put(key: String, value: String) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
        }
        val local = NativeLocalConnection(directory.resolve("local.sqlite").toString(), secrets, client)
        val metadataId: String
        val searchId: String
        init {
            fun install(host: String, resources: String, catalogs: String = "[]"): String = Json.parseToJsonElement(local.engine.installAddon("default", "https://$host/manifest.json",
                """{"id":"$host","name":"$host","version":"1","resources":$resources,"types":["movie","series"],"catalogs":$catalogs}""")).jsonObject.getValue("id").jsonPrimitive.content
            metadataId = install("metadata.example", "[\"meta\",\"stream\",\"subtitles\"]")
            searchId = install("search.example", "[\"catalog\"]", """[{"id":"all","type":"movie","extra":[{"name":"search"}]}]""")
        }
        suspend fun card() = local.search("default", "fixture").cards.single()
        override fun close() { releaseMetadata.complete(Unit); local.close(); directory.toFile().deleteRecursively() }
    }

    @Test fun searchOnlyPublicTitleUsesActualMetadataOriginForDetailsSourcesSubtitlesAndSaving() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val original = fixture.card()
            assertEquals(fixture.searchId, original.provider_id)
            val actual = fixture.local.detail("default", original).item
            assertEquals(fixture.metadataId, actual.provider_id)
            assertNotEquals(original.metadata_origin, actual.metadata_origin)
            assertEquals(original.identity.id, actual.identity.id)
            val sources = fixture.local.sources("default", original).candidates
            val input = fixture.local.openSource(sources.single().id)
            val subtitles = fixture.local.subtitles("default", original)
            assertTrue(fixture.local.subtitleContent(subtitles.candidates.single().id, input).decodeToString().contains("Delegated caption"))
            fixture.local.saveFavorite("default", original)
            assertEquals(actual.metadata_origin, fixture.local.library("default").single().metadata_origin)
            assertEquals(1, fixture.requests.count { "/meta/" in it }, "Verified A cache may serve the current B-origin public title")
            val before = fixture.requests.size
            assertFailsWith<LocalFailure> { fixture.local.detail("default", original.copy(identity = original.identity.copy(id = "opaque"))) }
            assertEquals(before, fixture.requests.size, "An IMDb hint cannot delegate an opaque lookup")
        }
    }

    @Test fun explicitEpisode1499UsesFullDelegatedMetadataWhileRetainingRawEpisodeSelector() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val original = fixture.card()
            val parent = MediaIdentity("tt1111111", "series", listOf(ExternalId("tmdb", "42")))
            val episode = original.copy(identity = MediaIdentity("tt1111111:1:1499", "series", emptyList(), episode = EpisodeCoordinates("aired", 1, 1499)), parent_identity = parent)
            val detail = fixture.local.detail("default", episode)
            assertEquals(24, detail.episodes.size)
            assertTrue(detail.episodes.none { it.identity.id == episode.identity.id })
            assertEquals(fixture.metadataId, detail.item.provider_id)
            val source = fixture.local.sources("default", episode).candidates.single()
            assertContains(fixture.local.openSource(source.id).uri, "delegated.mp4")
            val parentClaims = detail.item.identity.ids
            assertTrue(parentClaims.any { it.namespace == "tmdb" && it.value == "42" })
            assertTrue(fixture.requests.any { "/stream/series/tt1111111:1:1499.json" in it || "/stream/series/tt1111111%3A1%3A1499.json" in it })
            assertEquals(1, fixture.requests.count { "/meta/" in it })
        }
    }

    @Test fun issuerRevokedDuringDelegatedMetadataCannotPublishOrStartStreamIo() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val original = fixture.card(); fixture.holdMetadata = true
            val result = async { runCatching { fixture.local.sources("default", original) } }
            try {
                withTimeout(3000) { fixture.metadataStarted.await() }
                fixture.local.setAddonEnabled("default", fixture.searchId, false)
                fixture.releaseMetadata.complete(Unit)
                assertTrue(result.await().isFailure)
                assertTrue(fixture.requests.none { "/stream/" in it })
            } finally { fixture.releaseMetadata.complete(Unit); result.cancelAndJoin() }
        }
    }
}
