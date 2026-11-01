package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

internal const val xtreamConnectionsFixture = """{"items":[
{"id":1,"name":"Living room","enabled":true,"enable_live":true,"enable_movies":true,"enable_series":true,"credentials_encrypted":true,"refresh":{"state":"succeeded","error":null}},
{"id":2,"name":"Bedroom","enabled":true,"enable_live":true,"enable_movies":false,"enable_series":false,"credentials_encrypted":true,"refresh":{"state":"failed","error":"The provider catalog could not be refreshed."}}],"next_cursor":null}"""

private const val xtreamCreated = """{"id":3,"name":"Garage","enabled":true,"enable_live":true,"enable_movies":false,"enable_series":false,"credentials_encrypted":true,"refresh":{"state":"queued","error":null}}"""

private fun xtreamRecord(id: Int, name: String, live: Boolean = true, movies: Boolean = true, series: Boolean = true) =
    """{"id":$id,"name":"$name","enabled":true,"enable_live":$live,"enable_movies":$movies,"enable_series":$series,"credentials_encrypted":true,"refresh":{"state":"succeeded","error":null}}"""

class XtreamConnectionsTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private data class Sent(val path: String, val method: HttpMethod, val body: JsonObject?)

    private fun client(log: MutableList<Sent>, handle: MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        HttpClient(MockEngine { request ->
            val body = (request.body as? TextContent)?.text?.takeIf { it.isNotBlank() }?.let { Json.parseToJsonElement(it).jsonObject }
            log += Sent(request.url.encodedPath, request.method, body)
            handle(request)
        }) { configureTransport() }

    private fun MockRequestHandleScope.json(value: String) =
        respond(value, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    @Test fun thePageListsTheAccountsConnectionsAndSendsEachWriteOnce() = runBlocking {
        val log = mutableListOf<Sent>()
        val remote = RemoteConnection("https://watch.example", store, client(log) { request ->
            when (request.url.encodedPath) {
                "/api/iptv/connections" -> when (request.method) {
                    HttpMethod.Get -> json(xtreamConnectionsFixture)
                    HttpMethod.Post -> json(xtreamCreated)
                    else -> error("Unexpected connection method")
                }
                "/api/iptv/connections/1" -> when (request.method) {
                    HttpMethod.Patch -> json(xtreamRecord(1, "Living room TV", live = true, movies = false, series = true))
                    HttpMethod.Delete -> json("""{"ok":true}""")
                    else -> error("Unexpected connection method")
                }
                "/api/iptv/connections/2/credentials" -> json(xtreamRecord(2, "Bedroom"))
                "/api/iptv/connections/2/refresh" -> json("""{"state":"queued","error":null}""")
                "/api/iptv/live-default" -> when (request.method) {
                    HttpMethod.Put -> json("""{"catalog_id":2}""")
                    else -> json("""{"catalog_id":1}""")
                }
                else -> error("Unexpected request ${request.url.encodedPath}")
            }
        }, browserMode = true)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1")
        try {
            controller.refreshXtreamConnections(); controller.job!!.join()
            assertEquals(listOf("Living room", "Bedroom"), controller.state.value.xtreamConnections?.map { it.name })
            assertEquals(1L, controller.state.value.xtreamDefaultLive)
            assertEquals("Sync failed", controller.state.value.xtreamConnections?.last()?.let(::xtreamStatusLabel))

            controller.createXtreamConnection("Garage", "https://garage.example:8080", "viewer", "fixture-password",
                XtreamScopes(enabled = true, live = true, movies = false, series = false)); controller.job!!.join()
            assertEquals(listOf("Living room", "Bedroom", "Garage"), controller.state.value.xtreamConnections?.map { it.name })

            controller.updateXtreamConnection(1, "Living room TV", XtreamScopes(enabled = true, live = true, movies = false, series = true))
            controller.job!!.join()
            assertEquals("Living room TV", controller.state.value.xtreamConnections?.first()?.name)
            assertFalse(controller.state.value.xtreamConnections!!.first().scopes.movies)

            controller.replaceXtreamPassword(2, "replacement-password"); controller.job!!.join()
            controller.syncXtreamConnection(2); controller.job!!.join()
            assertEquals("queued", controller.state.value.xtreamConnections?.last()?.refresh?.state)
            controller.setXtreamDefaultLive(2); controller.job!!.join()
            assertEquals(2L, controller.state.value.xtreamDefaultLive)
            controller.deleteXtreamConnection(1); controller.job!!.join()
            assertEquals(listOf("Bedroom", "Garage"), controller.state.value.xtreamConnections?.map { it.name })

            val writes = log.filter { it.method != HttpMethod.Get }
            assertEquals(1, writes.count { it.path == "/api/iptv/connections" && it.method == HttpMethod.Post })
            assertEquals(1, writes.count { it.path == "/api/iptv/connections/1" && it.method == HttpMethod.Patch })
            assertEquals(1, writes.count { it.path == "/api/iptv/connections/1" && it.method == HttpMethod.Delete })
            assertEquals(1, writes.count { it.path == "/api/iptv/connections/2/credentials" && it.method == HttpMethod.Put })
            assertEquals(1, writes.count { it.path == "/api/iptv/connections/2/refresh" && it.method == HttpMethod.Post })
            assertEquals(1, writes.count { it.path == "/api/iptv/live-default" && it.method == HttpMethod.Put })
            assertEquals(6, writes.size, "each action sends one request: $writes")

            val created = writes.single { it.method == HttpMethod.Post && it.path == "/api/iptv/connections" }.body!!
            assertEquals(setOf("name", "url", "username", "password", "enabled", "enable_live", "enable_movies", "enable_series"),
                created.keys)
            assertEquals("Garage", created["name"]?.jsonPrimitive?.content)
            assertEquals("https://garage.example:8080", created["url"]?.jsonPrimitive?.content)
            assertEquals("viewer", created["username"]?.jsonPrimitive?.content)
            assertEquals("fixture-password", created["password"]?.jsonPrimitive?.content)
            assertEquals(false, created["enable_movies"]?.jsonPrimitive?.boolean)
            assertEquals(false, created["enable_series"]?.jsonPrimitive?.boolean)

            val edited = writes.single { it.method == HttpMethod.Patch }.body!!
            assertEquals(setOf("name", "enabled", "enable_live", "enable_movies", "enable_series"), edited.keys)
            assertEquals("Living room TV", edited["name"]?.jsonPrimitive?.content)
            assertEquals(true, edited["enable_series"]?.jsonPrimitive?.boolean)

            val renewed = writes.single { it.path == "/api/iptv/connections/2/credentials" }.body!!
            assertEquals(setOf("password"), renewed.keys, "replacing a password never resends the server address or username")
            assertEquals("replacement-password", renewed["password"]?.jsonPrimitive?.content)

            assertEquals(2, writes.single { it.path == "/api/iptv/live-default" }.body!!["catalog_id"]?.jsonPrimitive?.int)
            assertNull(writes.single { it.path == "/api/iptv/connections/2/refresh" }.body)
        } finally { remote.close() }
    }

    @Test fun aRejectedProviderLoginSaysWhatToCheck() = runBlocking {
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine {
            respond("""{"error":"The IPTV provider rejected access.","error_code":"provider_credentials_rejected"}""",
                HttpStatusCode.UnprocessableEntity, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = true)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1")
        try {
            controller.createXtreamConnection("Garage", "https://garage.example", "viewer", "wrong", XtreamScopes())
            controller.job!!.join()
            assertEquals("The provider rejected this login. Check the username, password and subscription.",
                controller.state.value.error)
            assertNull(controller.state.value.xtreamConnections)
        } finally { remote.close() }
    }

    @Test fun aPairedTelevisionIsToldToManageConnectionsElsewhere() = runBlocking {
        val remote = RemoteConnection("https://watch.example", store, HttpClient(MockEngine {
            respond("""{"error":"Use an account session.","error_code":"account_session_required"}""",
                HttpStatusCode.Forbidden, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }, browserMode = true)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1")
        try {
            controller.refreshXtreamConnections(); controller.job!!.join()
            assertEquals(xtreamManagedElsewhere, controller.state.value.error)
        } finally { remote.close() }
    }
}

private fun xtreamStatusLabel(connection: XtreamConnection): String =
    org.viptv.app.features.settings.xtreamSyncLabel(connection.refresh)
