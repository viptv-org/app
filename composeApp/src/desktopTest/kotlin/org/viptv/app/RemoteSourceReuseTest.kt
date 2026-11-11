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

class RemoteSourceReuseTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val card = Card(MediaIdentity("title", "movie", emptyList()), provider_id = "metadata", title = "Title", badges = emptyList())
    private val memory = SourceReuseContext(title = SourceReuseMemory("connection", listOf("en"), "Supplier", "supplier-id"))

    @Test fun discoveryOnlySendsOptionalMemoryAfterCapabilityNegotiationAndCarriesFallbackExclusion() = runBlocking {
        val requests = EngineRequests<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                requests += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                respond("""{"id":"job"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else respond("""{"events":[],"done":true,"phase":"preferred"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            assertNull(connection.sources(card, reuse = memory).discoveryPhase)
            assertFalse("remembered_source" in requests.single())
            connection.preferredSourceDiscoverySupported = true
            assertEquals("preferred", connection.sources(card, reuse = memory, device = DeviceCapability(maxHeight = 1080), excludePreferred = true).discoveryPhase)
            val request = requests.last()
            assertEquals("supplier-id", request.getValue("remembered_source").jsonObject["source_id"]?.jsonPrimitive?.content)
            assertEquals(1080, request["device_max_height"]?.jsonPrimitive?.int)
            assertEquals(true, request["exclude_preferred_source"]?.jsonPrimitive?.boolean)
        } finally { connection.close() }
    }

    @Test fun rankingNegotiatesOlderServersBeforeSendingMemoryAndKeepsDecisionOnCore() = runBlocking {
        val requests = EngineRequests<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            requests += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond("""{"streams":[{"id":"fresh","reuse_audio_language":"en"}],"source_reuse_supported":true,"source_reuse_ready":true,"source_reuse_ids":["fresh"]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            connection.preferredSourceDiscoverySupported = true
            val result = connection.rankSources("profile", listOf(SourceCandidate("fresh", "connection", "Source")), RankingContext(reuse = memory))
            assertEquals(2, requests.size)
            assertFalse("remembered_source" in requests.first())
            assertEquals("supplier-id", requests.last().getValue("remembered_source").jsonObject["source_id"]?.jsonPrimitive?.content)
            assertTrue(result.reuseReady)
            assertEquals(listOf("fresh"), result.reuseIds)
            assertEquals("en", result.reuseAudioLanguages["fresh"])
        } finally { connection.close() }
    }
    @Test fun olderServersCanRejectAffinityWithoutBreakingOrdinaryRanking() = runBlocking {
        val requests = EngineRequests<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            requests += body
            if ("provider_affinity" in body) respond("""{"error":"Unknown field"}""", HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, "application/json"))
            else respond("""{"streams":[{"id":"fresh"}]}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            val rows = listOf(SourceCandidate("fresh", "connection", "Source"))
            val context = RankingContext(providerAffinity = "connection")
            assertEquals("fresh", connection.rankSources("profile", rows, context).rows.single().id)
            assertTrue("provider_affinity" in requests.first())
            assertFalse("provider_affinity" in requests.last())
            connection.rankSources("profile", rows, context)
            assertEquals(3, requests.size)
            assertFalse("provider_affinity" in requests.last())
        } finally { connection.close() }
    }

}
