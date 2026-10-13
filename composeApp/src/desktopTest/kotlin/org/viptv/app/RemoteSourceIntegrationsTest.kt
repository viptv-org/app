package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import kotlin.test.*

class RemoteSourceIntegrationsTest {
    @Test fun configurationUsesDisplayedRevisionAndProfileScope() = runBlocking {
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val client = HttpClient(MockEngine { request ->
            requests += request.method to request.url.encodedPath
            val body = when (request.method) {
                HttpMethod.Get -> """{"revision":7,"connections":[],"schemas":[{"plugin_id":"fixture","name":"Fixture","state":"NeedsConfiguration","runtime_enabled":true,"credential_fields":[{"id":"key","label":"API key","required":true,"secret":true}],"provider_choices":[{"id":"provider-a","label":"Provider A"}],"maximum_providers":2}]}"""
                HttpMethod.Put -> {
                    val value = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertEquals(7, value.getValue("revision").jsonPrimitive.int)
                    assertEquals("fixture", value.getValue("plugin_id").jsonPrimitive.content)
                    assertEquals("private-fixture", value.getValue("configuration").jsonObject.getValue("key").jsonPrimitive.content)
                    "{}"
                }
                HttpMethod.Delete -> { assertEquals("7", request.url.parameters["revision"]); "{}" }
                else -> error("Unexpected method")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            val snapshot = remote.sourceIntegrationSchemas("12")
            assertEquals("provider-a", snapshot.schemas.single().provider_choices.single().id)
            assertTrue(snapshot.schemas.single().credential_fields.single().secret)
            remote.configureSourceIntegration("12", snapshot.revision, "fixture", buildJsonObject { put("key", "private-fixture") })
            remote.removeSourceIntegration("12", snapshot.revision, "fixture:1")
        } finally { remote.close() }
        assertEquals(listOf(HttpMethod.Get, HttpMethod.Put, HttpMethod.Delete), requests.map { it.first })
        assertTrue(requests.all { it.second.startsWith("/api/v3/profiles/12/source-integrations") })
    }
}
