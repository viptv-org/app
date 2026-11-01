package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import kotlin.test.*

class RemoteGatewaysTest {
    private data class Call(val method: HttpMethod, val path: String, val body: String?)

    @Test fun gatewayOperationsAreEachSentOnceWithTheExpectedBody() = runBlocking {
        val fakeKey = "pgk_" + "a".repeat(64)
        val calls = mutableListOf<Call>()
        val client = HttpClient(MockEngine { request ->
            val text = (request.body as? TextContent)?.text
            calls += Call(request.method, request.url.encodedPath, text)
            val body = when {
                request.url.encodedPath == "/api/gateways" && request.method == HttpMethod.Get ->
                    """{"items":[{"id":"g1","name":"Beta transcoder","endpoint":"https://gateway.example/","namespace":"embedez-kmp","priority":100,"enabled":true,"revision":3,"can_manage":true}],"secret_storage_configured":true}"""
                request.url.encodedPath.endsWith("/check") ->
                    """{"ready":true,"version":1,"available":{"inputs":4,"outputs":4,"viewers":16}}"""
                request.url.encodedPath == "/api/gateways" && request.method == HttpMethod.Post ->
                    """{"id":"g2","name":"New","endpoint":"https://edge.example/","namespace":"ns","priority":50,"enabled":true,"revision":1,"can_manage":true}"""
                request.url.encodedPath.endsWith("/grants") && request.method == HttpMethod.Get ->
                    """{"items":[{"account_id":7,"enabled":true}],"next_cursor":null}"""
                else -> "{}"
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }, client, browserMode = false)
        try {
            val snapshot = remote.gateways()
            assertTrue(snapshot.secret_storage_configured)
            assertEquals("Beta transcoder", snapshot.items.single().name)
            assertTrue(snapshot.items.single().can_manage)

            val check = remote.checkGateway("g1")
            assertTrue(check.ready)
            assertEquals(4, check.available?.inputs)
            assertEquals(16, check.available?.viewers)

            remote.updateGateway("g1", enabled = false)
            remote.registerGateway("New", "https://edge.example/", "ns", 50, fakeKey)
            remote.deleteGateway("g2")

            assertEquals(listOf(7L), remote.gatewayGrants("g1").items.map { it.account_id })
            remote.grantGateway("g1", 9L)
            remote.revokeGateway("g1", 7L)
        } finally { remote.close() }

        assertEquals(listOf(
            HttpMethod.Get to "/api/gateways",
            HttpMethod.Post to "/api/gateways/g1/check",
            HttpMethod.Patch to "/api/gateways/g1",
            HttpMethod.Post to "/api/gateways",
            HttpMethod.Delete to "/api/gateways/g2",
            HttpMethod.Get to "/api/gateways/g1/grants",
            HttpMethod.Put to "/api/gateways/g1/grants",
            HttpMethod.Put to "/api/gateways/g1/grants",
        ), calls.map { it.method to it.path })

        assertEquals("""{"enabled":false}""", Json.parseToJsonElement(calls[2].body!!).let { it.toString() })
        val registration = Json.parseToJsonElement(calls[3].body!!).jsonObject
        assertEquals("New", registration.getValue("name").jsonPrimitive.content)
        assertEquals("https://edge.example/", registration.getValue("endpoint").jsonPrimitive.content)
        assertEquals("ns", registration.getValue("namespace").jsonPrimitive.content)
        assertEquals(50, registration.getValue("priority").jsonPrimitive.int)
        assertEquals(fakeKey, registration.getValue("integration_key").jsonPrimitive.content)
        assertEquals("""{"account_id":9,"enabled":true}""", Json.parseToJsonElement(calls[6].body!!).let { it.toString() })
        assertEquals("""{"account_id":7,"enabled":false}""", Json.parseToJsonElement(calls[7].body!!).let { it.toString() })
    }

    @Test fun listWithoutSecretStorageReportsIt() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            respond("""{"items":[],"secret_storage_configured":false}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }, client, browserMode = false)
        try {
            val snapshot = remote.gateways()
            assertFalse(snapshot.secret_storage_configured)
            assertTrue(snapshot.items.isEmpty())
        } finally { remote.close() }
    }
}