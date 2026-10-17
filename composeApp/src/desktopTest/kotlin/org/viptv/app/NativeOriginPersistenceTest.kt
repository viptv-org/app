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

/** Actual Rust/SQLite/UniFFI and native adapters; provider HTTP is the only replacement. */
class NativeOriginPersistenceTest {
    private class Fixture : AutoCloseable {
        val directory = Files.createTempDirectory("viptv-native-origin-bridge-")
        val path = directory.resolve("local.sqlite").toString()
        val ids = mutableMapOf("first.example" to "tt1111111", "second.example" to "tt2222222")
        val requests = mutableListOf<String>()
        val secrets = object : SecretStore {
            val values = ConcurrentHashMap<String, String>()
            override fun get(key: String) = values[key]
            override fun put(key: String, value: String) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
        }
        fun client() = HttpClient(MockEngine { request ->
            val host = request.url.host; val route = request.url.encodedPath
            val metadataId = if (route.endsWith("/other.json")) "other" else "opaque"
            synchronized(requests) { requests += "$host$route" }
            val body = when {
                "/meta/" in route -> """{"meta":{"id":"$metadataId","type":"movie","name":"$host title","imdb_id":"${ids.getValue(host)}"}}"""
                "/stream/" in route -> """{"streams":[{"name":"$host 1080p","url":"https://media.example/$host.mp4"}]}"""
                "/subtitles/" in route -> """{"subtitles":[{"id":"subtitle","lang":"en","url":"https://subtitles.example/$host.vtt"}]}"""
                host == "subtitles.example" -> "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nOwned caption\n"
                else -> error("Unexpected provider request: $host$route")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, if (host == "subtitles.example") "text/vtt" else "application/json"))
        }) { configureTransport() }
        val local = NativeLocalConnection(path, secrets, client())
        val previews: List<Card>
        init {
            for (host in listOf("first.example", "second.example")) local.engine.installAddon("default", "https://$host/manifest.json",
                """{"id":"$host","name":"$host","version":"1","resources":["catalog","meta","stream","subtitles"],"types":["movie"],"catalogs":[{"id":"all","type":"movie","extra":[{"name":"search"}]}]}""")
            val plan = Json.parseToJsonElement(local.engine.planSearch("default", "fixture")).jsonObject
            val responses = plan.getValue("requests").jsonArray.map { request -> buildJsonObject {
                put("id", request.jsonObject.getValue("id"))
                put("body", buildJsonObject { put("metas", JsonArray(listOf(buildJsonObject {
                    put("id", "opaque"); put("type", "movie"); put("name", request.jsonObject.getValue("title").jsonPrimitive.content)
                }))) }.toString())
            } }
            val snapshot = local.json.decodeFromString<HomeSnapshot>(local.engine.projectHome(plan.getValue("id").jsonPrimitive.content, JsonArray(responses).toString()))
            previews = snapshot.rows.flatMap { it.cards }.sortedBy { it.title }
        }
        override fun close() { local.close(); directory.toFile().deleteRecursively() }
    }

    @Test fun nativeRoundTripKeepsAliasStateAndRejectsWrongInputSubtitlesAndRemappedLookup() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val local = fixture.local
            val first = local.detail("default", fixture.previews[0]).item
            val second = local.detail("default", fixture.previews[1]).item
            assertNotEquals(first.metadata_origin, second.metadata_origin)
            local.saveFavorite("default", first); local.saveFavorite("default", second)
            val membership = local.libraryMembership("default")
            assertEquals(setOf("tt1111111", "tt2222222"), membership.identities.map { it.id }.toSet())
            assertEquals(2, membership.scoped_identities.size)
            assertTrue(membership.scoped_identities.all { it.identity.ids.isNotEmpty() })
            val sourcesA = local.sources("default", first)
            val sourcesB = local.sources("default", second)
            assertEquals(1, sourcesA.size); assertEquals(1, sourcesB.size)
            assertEquals(first.provider_id, sourcesA.single().provider_id)
            val inputA = local.openSource(sourcesA.single().id); val inputB = local.openSource(sourcesB.single().id)
            local.recordProgress(inputA, first, 4000, 100000); local.recordProgress(inputB, second, 8000, 100000)
            val subtitlesA = local.subtitles("default", first)
            assertTrue(local.subtitleContent(subtitlesA.candidates.single().id, inputA).decodeToString().contains("Owned caption"))
            val subtitlesB = local.subtitles("default", second)
            val before = fixture.requests.count { it.startsWith("subtitles.example") }
            assertFailsWith<LocalFailure> { local.subtitleContent(subtitlesB.candidates.single().id, inputA) }
            assertEquals(before, fixture.requests.count { it.startsWith("subtitles.example") }, "Wrong-origin caption must not be fetched")
            val other = local.detail("default", first.copy(identity = first.identity.copy(id = "other", ids = emptyList()))).item
            val otherSubtitles = local.subtitles("default", other)
            val beforeOther = fixture.requests.count { it.startsWith("subtitles.example") }
            assertFailsWith<LocalFailure> { local.subtitleContent(otherSubtitles.candidates.single().id, inputA) }
            assertEquals(beforeOther, fixture.requests.count { it.startsWith("subtitles.example") }, "Same-addon wrong-title caption must not be fetched")
            val host = if (inputA.uri.contains("first.example")) "first.example" else "second.example"
            fixture.ids[host] = "tt3333333"
            val streamsBefore = fixture.requests.count { "/stream/" in it }
            assertFailsWith<LocalFailure> { local.sources("default", first) }
            assertEquals(streamsBefore, fixture.requests.count { "/stream/" in it }, "Remapped identity must fail before stream IO")
            val remapped = local.detail("default", fixture.previews.first { it.provider_id == first.provider_id }).item
            val remappedSubtitles = local.subtitles("default", remapped)
            val beforeRemapped = fixture.requests.count { it.startsWith("subtitles.example") }
            assertFailsWith<LocalFailure> { local.subtitleContent(remappedSubtitles.candidates.single().id, inputA) }
            assertEquals(beforeRemapped, fixture.requests.count { it.startsWith("subtitles.example") }, "Same-addon remapped lookup caption must not attach to old playback")
            local.removeFavorite("default", first)
            assertEquals(setOf("tt2222222"), local.libraryMembership("default").identities.map { it.id }.toSet())
            val requestsBefore = fixture.requests.size
            assertFailsWith<LocalFailure> { local.detail("default", first.copy(metadata_origin = null)) }
            assertEquals(requestsBefore, fixture.requests.size, "Legacy opaque cards remain unresolved")
            val publicClaim = first.copy(identity = MediaIdentity("tt1234567", "movie", listOf(ExternalId("imdb", "tt1234567"))))
            val streamsBeforeMismatch = fixture.requests.count { "/stream/" in it }
            assertFailsWith<LocalFailure> { local.sources("default", publicClaim) }
            assertEquals(streamsBeforeMismatch, fixture.requests.count { "/stream/" in it }, "Known wrong-ID metadata must not fall back to unverified public-source discovery")

        }
    }

    @Test fun nativeMembershipAndResumeSurviveReopenWithStableIncarnation() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val first = fixture.local.detail("default", fixture.previews.first()).item
            fixture.local.saveFavorite("default", first)
            val source = fixture.local.sources("default", first).single()
            val input = fixture.local.openSource(source.id)
            fixture.local.recordProgress(input, first, 17000, 100000)
            fixture.local.close()
            val reopened = NativeLocalConnection(fixture.path, fixture.secrets, fixture.client())
            try {
                val membership = reopened.libraryMembership("default")
                assertEquals(first.metadata_origin, membership.scoped_identities.single().metadata_origin)
                val resumed = reopened.openSource(reopened.sources("default", first).single().id)
                assertEquals(17000, resumed.positionMillis)
                assertEquals(first.metadata_origin, reopened.library("default").single().metadata_origin)
            } finally { reopened.close() }
        }
    }
}
