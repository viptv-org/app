package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import kotlin.test.*

class RemoteAddonsTest {
    private class MemoryStore : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val configuration = """{"configuration":{"scope":{"connection_id":"server","account_id":"7","profile_id":"12","revision":9},"mode":"Server","name":"Parent","browsing_plugin":"stremio","assignments":[{"connection_id":"addon:1","enabled":["Sources"]},{"connection_id":"simkl:2","enabled":["Tracking"]}]},"connections":[{"id":"addon:1","plugin_id":"stremio","account_id":"7","name":"Existing","enabled":true,"revision":3,"capabilities":["Sources"]},{"id":"addon:2","plugin_id":"stremio","account_id":"7","name":"New","enabled":true,"revision":4,"capabilities":["Sources","Metadata"]},{"id":"simkl:2","plugin_id":"simkl","account_id":"7","name":"Tracking","enabled":true,"revision":1,"capabilities":["Tracking"]}]}"""

    @Test fun profileTogglePreservesOtherAssignmentsAndUsesRevision() = runBlocking {
        val mutations = mutableListOf<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("https://watch.example", request.headers[HttpHeaders.Origin])
            assertEquals("/api/profiles/12/integrations", request.url.encodedPath)
            if (request.method == HttpMethod.Put) mutations += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            else assertEquals(HttpMethod.Get, request.method)
            respond(configuration, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", MemoryStore(), client, browserMode = false)
        connection.setAddonEnabled("12", "addon:1", false)
        connection.setAddonEnabled("12", "addon:2", true)
        assertEquals(9, mutations[0].getValue("revision").jsonPrimitive.int)
        assertEquals(listOf("simkl:2"), mutations[0].getValue("assignments").jsonArray.map { it.jsonObject.getValue("connection_id").jsonPrimitive.content })
        assertEquals(listOf("addon:1", "simkl:2", "addon:2"), mutations[1].getValue("assignments").jsonArray.map { it.jsonObject.getValue("connection_id").jsonPrimitive.content })
        assertEquals(listOf("Sources", "Metadata"), mutations[1].getValue("assignments").jsonArray.last().jsonObject.getValue("enabled").jsonArray.map { it.jsonPrimitive.content })
        connection.close()
    }

    @Test fun installAndRemoveUseAccountEndpointAndInventoryFollowsPagination() = runBlocking {
        val writes = mutableListOf<String>()
        var pageCount = 0
        val client = HttpClient(MockEngine { request ->
            val result = when {
                request.url.encodedPath.endsWith("/integrations") -> { if (request.method == HttpMethod.Put) writes += "assign"; configuration }
                request.url.encodedPath == "/api/addons" && request.method == HttpMethod.Post -> {
                    assertEquals("https://fixture.example/manifest.json", Json.parseToJsonElement((request.body as TextContent).text).jsonObject.getValue("manifest_url").jsonPrimitive.content)
                    writes += "install"; """{"id":2}"""
                }
                request.url.encodedPath == "/api/addons/2" -> { assertEquals(HttpMethod.Delete, request.method); writes += "remove"; """{"ok":true}""" }
                else -> {
                    assertEquals(HttpMethod.Get, request.method)
                    pageCount++
                    if (pageCount == 1) """{"items":[{"id":1,"name":"Existing","enabled":true}],"next_cursor":"next"}"""
                    else { assertEquals("next", request.url.parameters["cursor"]); """{"items":[{"id":3,"name":"Disabled","enabled":false}],"next_cursor":null}""" }
                }
            }
            respond(result, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", MemoryStore(), client, browserMode = false)
        connection.installAddon("12", "https://fixture.example/manifest.json")
        val inventory = connection.addons("12")
        assertEquals(listOf("addon:1", "addon:3"), inventory.addons.map { it.id })
        assertFalse(inventory.addons.last().enabled)
        assertTrue(inventory.addons.last().capabilities.isEmpty())
        connection.removeAddon("addon:2")
        assertEquals(listOf("install", "assign", "remove"), writes)
        assertFailsWith<RemoteFailure> { connection.removeAddon("simkl:2") }
        connection.close()
    }

    @Test fun inventoryKeepsOnlyTheServerLogoRouteAndFetchesItWithTheSession() = runBlocking {
        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        val requested = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/integrations") -> respond(configuration, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                request.url.encodedPath == "/api/addons" -> respond("""{"items":[{"id":1,"name":"Existing","enabled":true,"logo":"/api/addons/1/logo?v=ab12"},""" +
                    """{"id":2,"name":"New","enabled":true,"logo":"https://art.example/private-token/logo.png"},{"id":3,"name":"Other","enabled":true,"logo":"/api/addons/1/logo?v=ab12"},""" +
                    """{"id":4,"name":"Plain","enabled":true,"logo":null}],"next_cursor":null}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
                else -> {
                    requested += request.url.encodedPath + "?" + request.url.encodedQuery
                    respond(png, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                }
            }
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", MemoryStore(), client, browserMode = false)
        val inventory = connection.addons("12").addons.associate { it.id to it.logo }
        assertEquals(mapOf("addon:1" to "/api/addons/1/logo?v=ab12", "addon:2" to null, "addon:3" to null, "addon:4" to null), inventory)
        assertContentEquals(png, connection.addonLogo("/api/addons/1/logo?v=ab12"))
        assertEquals(listOf("/api/addons/1/logo?v=ab12"), requested)
        assertFailsWith<RemoteFailure> { connection.addonLogo("/api/profiles/12/integrations") }
        assertFailsWith<RemoteFailure> { connection.addonLogo("/api/addons/1/logo?v=ab12&next=https://art.example") }
        connection.close()
    }
}
