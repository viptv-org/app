package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import kotlin.test.*

class VodMatchesTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val headers get() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun controller(scope: CoroutineScope,
        handle: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): ApplicationController {
        val client = HttpClient(MockEngine(handle)) { configureTransport() }
        return ApplicationController(scope, store).also {
            it.connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
            it.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1")
        }
    }

    private fun match(vodId: String, providerId: Long = 1, type: String = "movie", name: String = "A title", year: Int? = 1999) =
        """{"vod_id":"$vodId","provider_id":$providerId,"type":"$type","name":"$name","year":${year ?: "null"},"poster":null}"""

    private fun page(vararg rows: String, next: String? = null) =
        """{"items":[${rows.joinToString(",")}],"next_cursor":${next?.let { "\"$it\"" } ?: "null"},"previous_cursor":null}"""

    private val providers =
        """{"items":[{"id":1,"name":"Home Xtream","enabled":true},{"id":3,"name":"Second host","enabled":true}],"next_cursor":null}"""

    private suspend fun await(what: String, predicate: () -> Boolean) {
        withTimeout(5_000) {
            while (!predicate()) delay(10)
        }.let { }
        check(predicate()) { "timed out waiting for $what" }
    }

    private suspend fun ApplicationController.awaitState(what: String, predicate: (ApplicationState) -> Boolean): ApplicationState =
        withTimeout(5_000) { state.first(predicate) }.also { check(predicate(it)) { "unexpected state after $what" } }

    @Test fun providerTypeAndSearchChangesSendTheFilteredRequest() = runBlocking<Unit> {
        val requests = mutableListOf<Url>()
        val controller = controller(this) { request ->
            when (request.url.encodedPath) {
                "/api/iptv/connections" -> respond(providers, headers = headers)
                "/api/iptv/matches" -> {
                    requests += request.url
                    respond(page(match("vod:1:1"), match("vod:3:2", 3, "series", "A series")), headers = headers)
                }
                else -> error("unexpected ${request.url}")
            }
        }
        try {
            controller.openVodMatches()
            await("the first page") { requests.size == 1 }
            val opened = controller.awaitState("the first page") { it.vodMatches?.loaded == true && it.vodMatches?.loading == false }
            assertEquals(listOf(1L to "Home Xtream", 3L to "Second host"),
                opened.vodMatches!!.providers.map { it.id to it.name })
            assertEquals(listOf("vod:1:1", "vod:3:2"), opened.vodMatches!!.rows.map { it.vodId })
            assertEquals("50", requests.single().parameters["limit"])
            assertNull(requests.single().parameters["provider_id"])

            controller.updateVodMatchProvider(3)
            await("the provider request") { requests.size == 2 }
            assertEquals("3", requests.last().parameters["provider_id"])
            assertNull(requests.last().parameters["kind"])
            assertNull(requests.last().parameters["search"])

            controller.updateVodMatchType("series")
            await("the type request") { requests.size == 3 }
            assertEquals("3", requests.last().parameters["provider_id"])
            assertEquals("series", requests.last().parameters["kind"])

            controller.searchVodMatches("Star")
            controller.searchVodMatches("Star Wars")
            delay(150)
            assertEquals(3, requests.size, "the replaced keystroke never reaches the host")
            await("the debounced search") { requests.size == 4 }
            assertEquals("Star Wars", requests.last().parameters["search"])
            assertEquals("3", requests.last().parameters["provider_id"])
            assertEquals("series", requests.last().parameters["kind"])
            delay(400)
            assertEquals(4, requests.size, "the search runs once")
            val searched = controller.state.value.vodMatches!!
            assertEquals("Star Wars", searched.query)
            assertTrue(searched.filtered)
            assertNull(searched.error)
        } finally { controller.connection?.close() }
    }

    @Test fun overridingAMatchSendsThePutOnceAndUpdatesTheRowInPlace() = runBlocking<Unit> {
        val writes = mutableListOf<JsonObject>()
        val listRequests = mutableListOf<Url>()
        val controller = controller(this) { request ->
            when {
                request.url.encodedPath == "/api/iptv/connections" -> respond(providers, headers = headers)
                request.method == HttpMethod.Put -> {
                    writes += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    respond("""{"ok":true}""", headers = headers)
                }
                else -> {
                    listRequests += request.url
                    respond(page(match("vod:1:1", name = "First"), match("vod:1:2", name = "Second"),
                        match("vod:1:3", name = "Third")), headers = headers)
                }
            }
        }
        try {
            controller.openVodMatches()
            await("the first page") { listRequests.size == 1 }
            controller.awaitState("the first page") { it.vodMatches?.loaded == true }
            controller.updateVodMatchProvider(1)
            await("the provider request") { listRequests.size == 2 }
            val before = controller.awaitState("the provider page") {
                it.vodMatches?.providerId == 1L && it.vodMatches?.loading == false
            }.vodMatches!!

            controller.openVodMatchForm(before.rows[1])
            controller.changeVodMatchMetadata("tt0111161")
            controller.saveVodMatch()
            val after = controller.awaitState("the saved match") { it.vodMatches?.form == null && it.vodMatches?.saving == false }.vodMatches!!

            assertEquals(1, writes.size)
            assertEquals(setOf("vod_id", "metadata_id", "type"), writes.single().keys)
            assertEquals("vod:1:2", writes.single()["vod_id"]!!.jsonPrimitive.content)
            assertEquals("tt0111161", writes.single()["metadata_id"]!!.jsonPrimitive.content)
            assertEquals("movie", writes.single()["type"]!!.jsonPrimitive.content)
            assertEquals(before.rows.map { it.vodId }, after.rows.map { it.vodId })
            assertNull(after.rows[0].metadataId)
            assertEquals("tt0111161", after.rows[1].metadataId)
            assertNull(after.rows[2].metadataId)
            assertEquals(1L, after.providerId)
            assertEquals("Match saved.", after.notice)
            assertNull(after.error)
            assertEquals(2, listRequests.size, "saving keeps the current page instead of reloading it")
        } finally { controller.connection?.close() }
    }

    @Test fun emptyResultsAndHostFailuresLeaveThePageWithAnExplicitState() = runBlocking<Unit> {
        var failing = false
        val matches = mutableListOf<Url>()
        val controller = controller(this) { request ->
            when {
                request.url.encodedPath == "/api/iptv/connections" -> respond(providers, headers = headers)
                failing -> respond("""{"error_code":"provider_storage_unavailable"}""", HttpStatusCode.ServiceUnavailable,
                    headers = headers)
                else -> {
                    matches += request.url
                    respond("""{"items":[],"next_cursor":null,"previous_cursor":null}""", headers = headers)
                }
            }
        }
        try {
            controller.openVodMatches()
            await("the empty page") { matches.size == 1 }
            val empty = controller.awaitState("the empty page") { it.vodMatches?.loaded == true && it.vodMatches?.loading == false }
            assertTrue(empty.vodMatches!!.rows.isEmpty())
            assertFalse(empty.vodMatches!!.filtered)
            assertNull(empty.vodMatches!!.error)

            controller.searchVodMatches("nothing")
            await("the filtered empty page") { matches.size == 2 }
            val filtered = controller.awaitState("the filtered empty page") {
                it.vodMatches?.query == "nothing" && it.vodMatches?.loading == false
            }.vodMatches!!
            assertTrue(filtered.rows.isEmpty())
            assertTrue(filtered.filtered)
            assertEquals("nothing", matches.last().parameters["search"])

            failing = true
            controller.refreshVodMatches()
            val failed = controller.awaitState("the host failure") { it.vodMatches?.error != null }.vodMatches!!
            assertTrue(failed.loaded)
            assertFalse(failed.loading)
            assertNotNull(failed.error)
        } finally { controller.connection?.close() }
    }
}