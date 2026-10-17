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

class NativeMetadataOriginTest {
    @Test fun identicalProviderIdsKeepSeparateCachePagesAndDisabledOriginDoesNotFallThrough() = runBlocking<Unit> {
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requests += request.url.host
            val name = if (request.url.host == "first.example") "First" else "Second"
            val videos = (1..60).map { number -> buildJsonObject {
                put("id", "shared-episode-$number"); put("name", "$name episode $number"); put("season", 1); put("episode", number)
            } }
            val body = buildJsonObject { put("meta", buildJsonObject {
                put("id", "shared-id"); put("name", name); put("type", "series"); put("videos", JsonArray(videos))
            }) }.toString()
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val directory = Files.createTempDirectory("viptv-native-metadata-origin-")
        val secrets = object : SecretStore {
            val values = ConcurrentHashMap<String, String>()
            override fun get(key: String) = values[key]
            override fun put(key: String, value: String) { values[key] = value }
            override fun remove(key: String) { values.remove(key) }
        }
        val local = NativeLocalConnection(directory.resolve("local.sqlite").toString(), secrets, client)
        try {
            val cards = listOf("first.example", "second.example").map { host ->
                val installed = Json.parseToJsonElement(local.engine.installAddon("default", "https://$host/manifest.json",
                    """{"id":"$host","name":"$host","version":"1","resources":["meta"],"types":["series"]}""")).jsonObject
                val provider = installed.getValue("id").jsonPrimitive.content
                // Obtain an origin through a real host projection. Its nonce is
                // public provenance, not a credential or inferred connection.
                val plan = Json.parseToJsonElement(local.engine.planDetail("default", "series", "tt1234567", provider)).jsonObject
                val projected = local.json.decodeFromString<DetailSnapshot>(local.engine.projectDetail(plan.getValue("operation").jsonPrimitive.content,
                    """{"meta":{"id":"tt1234567","type":"series","name":"Origin fixture"}}""", 0u))
                projected.item.copy(identity = MediaIdentity("shared-id", "series", emptyList()), provider_id = provider, title = host)
            }
            assertFailsWith<LocalFailure> { local.detail("default", cards.first().copy(metadata_origin = null), 0) }
            assertTrue(requests.isEmpty(), "Legacy opaque data must not be assigned today's incarnation or sent upstream")
            val first = local.detail("default", cards.first(), 0)
            val second = local.detail("default", cards.last(), 0)
            assertEquals("First", first.item.title); assertEquals("Second", second.item.title)
            assertEquals(listOf("first.example", "second.example"), requests)
            assertEquals("First", local.detail("default", cards.first(), 0).item.title)
            val firstPage = local.detailPage("default", cards.first(), 24, 24)
            val secondPage = local.detailPage("default", cards.last(), 24, 24)
            assertEquals("First episode 25", firstPage.episodes.first().title)
            assertEquals("Second episode 25", secondPage.episodes.first().title)
            assertTrue(firstPage.episodes.all { it.provider_id == cards.first().provider_id })
            assertTrue(secondPage.episodes.all { it.provider_id == cards.last().provider_id })
            assertEquals(2, requests.size, "Paging reuses only the selected producer's cached body")
            local.setAddonEnabled("default", cards.first().provider_id, false)
            assertFailsWith<LocalFailure> { local.detail("default", cards.first(), 0) }
            assertEquals(2, requests.size, "A disabled origin cannot send its opaque ID to a sibling")
        } finally { local.close(); directory.toFile().deleteRecursively() }
    }
}
