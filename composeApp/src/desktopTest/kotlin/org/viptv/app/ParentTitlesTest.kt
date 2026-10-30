package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class ParentTitlesTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private fun card(id: String = "tt123") = Card(MediaIdentity(id, "movie", emptyList()), provider_id = "addon:7", title = "A title", badges = emptyList())
    private fun controller(scope: CoroutineScope, browser: Boolean = true, handle: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): ApplicationController {
        val client = HttpClient(MockEngine(handle)) { configureTransport() }
        return ApplicationController(scope, store).also {
            it.connection = RemoteConnection("https://watch.example", store, client, browserMode = browser)
            it.mutable.value = ApplicationState(screen = Screen.Settings, selectedProfile = "1", parentSettings = parentFixture(kids = true))
        }
    }

    @Test fun nativeSessionsApproveAndRevokeTitlesThroughFakeHost() = runBlocking<Unit> {
        val row = ParentTitleRow("addon-7", "My addon", "stremio", 7, items = listOf(ParentTitleItem(card(), true)))
        val writes = mutableListOf<Pair<String, JsonObject>>()
        val controller = controller(this, browser = false) { request ->
            val result = when {
                request.url.encodedPath == "/api/parent/search" -> Json.encodeToString(ParentTitleSearch(listOf(row)))
                request.method == HttpMethod.Post -> {
                    writes += request.url.encodedPath to Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    "{\"approved\":true}"
                }
                else -> "{\"items\":[{\"id\":\"tt123\",\"type\":\"movie\",\"name\":\"A title\"}]}"
            }
            respond(result, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            controller.openParentTitles("2"); controller.job!!.join()
            controller.searchParentTitles("A title"); controller.job!!.join()
            controller.approveParentTitle(row.id, row.items.single().card); controller.job!!.join()
            controller.revokeParentTitle(controller.state.value.parentTitles!!.approved.single()); controller.job!!.join()
            assertEquals(listOf("/api/profiles/2/approvals", "/api/profiles/2/approvals"), writes.map { it.first })
            assertEquals(true, writes[0].second["approved"]!!.jsonPrimitive.boolean)
            assertEquals(false, writes[1].second["approved"]!!.jsonPrimitive.boolean)
            assertEquals("tt123", writes[1].second["id"]!!.jsonPrimitive.content)
            assertNull(controller.state.value.error)
        } finally { controller.connection?.close() }
    }

    @Test fun typedSearchKeepsProviderRowsAndApprovalSendsOnlyTrustedSelector() = runBlocking<Unit> {
        val row = ParentTitleRow("addon-7", "My addon", "stremio", 7, items = listOf(ParentTitleItem(card(), true)))
        var write: JsonObject? = null
        val controller = controller(this) { request ->
            val result = when {
                request.url.encodedPath == "/api/parent/search" -> {
                    assertEquals("a & b", request.url.parameters["query"])
                    assertEquals("2", request.url.parameters["profile_id"])
                    Json.encodeToString(ParentTitleSearch(listOf(row)))
                }
                request.method == HttpMethod.Post -> {
                    assertEquals("/api/profiles/2/approvals", request.url.encodedPath)
                    write = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    "{\"approved\":true}"
                }
                else -> if (write != null) """{"items":[{"id":"tt123","type":"movie","name":"A title","metadata_provider_id":"stremio","approval_supported":true}]}""" else "{\"items\":[]}"
            }
            respond(result, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            controller.openParentTitles("2"); controller.job!!.join()
            controller.searchParentTitles(" a & b "); controller.job!!.join()
            assertEquals(row, controller.state.value.parentTitles!!.rows.single())
            controller.approveParentTitle(row.id, row.items.single().card); controller.job!!.join()
            assertEquals(setOf("type", "id", "approved", "metadata_provider_id", "metadata_addon_id"), write!!.keys)
            assertEquals(7, write!!["metadata_addon_id"]!!.jsonPrimitive.int)
            assertEquals("stremio", write!!["metadata_provider_id"]!!.jsonPrimitive.content)
            assertEquals("tt123", controller.state.value.parentTitles!!.approved.single().id)
        } finally { controller.connection?.close() }
    }

    @Test fun unsupportedSearchResultCannotDispatchApproval() = runBlocking<Unit> {
        var calls = 0
        val controller = controller(this) { calls++; error("No request expected") }
        controller.mutable.value = controller.mutable.value.copy(parentTitles = ParentTitlesState("2", "Child", rows = listOf(
            ParentTitleRow("private", "Private addon", "stremio", 7, items = listOf(ParentTitleItem(card("private:1"), false, "Unsupported private title"))))))
        try {
            controller.approveParentTitle("private", card("private:1")); controller.job!!.join()
            assertEquals(0, calls); assertContains(controller.state.value.error.orEmpty(), "Unsupported")
        } finally { controller.connection?.close() }
    }

    @Test fun legacyApprovalCanBeRemovedWithoutInventedProviderAndClearsCurrentContentBeforeRequest() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var write: JsonObject? = null
        val controller = controller(this) { request ->
            if (request.method == HttpMethod.Post) {
                write = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                started.complete(Unit); release.await()
                respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else respond("{\"items\":[]}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        controller.mutable.value = controller.mutable.value.copy(selectedProfile = "2", results = listOf(card()),
            parentTitles = ParentTitlesState("2", "Child", approved = listOf(ApprovedTitle("legacy:1", "movie")), loaded = true))
        try {
            controller.revokeParentTitle(ApprovedTitle("legacy:1", "movie")); started.await()
            assertTrue(controller.state.value.results.isEmpty()); assertTrue(controller.state.value.policyRefreshNeeded)
            release.complete(Unit); controller.job!!.join()
            assertEquals(setOf("type", "id", "approved"), write!!.keys)
            assertFalse(write!!["approved"]!!.jsonPrimitive.boolean)
            assertTrue(controller.state.value.parentTitles!!.approved.isEmpty())
        } finally { release.complete(Unit); controller.connection?.close() }
    }

    @Test fun expiredParentGrantClearsPrivilegedSearchResults() = runBlocking<Unit> {
        val controller = controller(this) { request ->
            respond(if (request.url.encodedPath == "/api/profiles") Json.encodeToString(parentFixture(unlocked = false)) else "{}",
                if (request.url.encodedPath == "/api/profiles") HttpStatusCode.OK else HttpStatusCode.Forbidden,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        controller.mutable.value = controller.mutable.value.copy(parentTitles = ParentTitlesState("2", "Child"))
        try {
            controller.searchParentTitles("title"); controller.job!!.join()
            assertNull(controller.state.value.parentTitles); assertContains(controller.state.value.error.orEmpty(), "Unlock")
        } finally { controller.connection?.close() }
    }

    @Test fun leavingSettingsCancelsLateParentResults() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val controller = controller(this) {
            started.complete(Unit)
            withContext(NonCancellable) { release.await() }
            respond("{\"rows\":[]}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        controller.mutable.value = controller.mutable.value.copy(parentTitles = ParentTitlesState("2", "Child"))
        try {
            controller.searchParentTitles("late"); started.await(); val job = controller.job!!
            controller.navigate(Screen.Home); release.complete(Unit); job.join()
            assertNull(controller.state.value.parentTitles); assertEquals(Screen.Home, controller.state.value.screen)
        } finally { release.complete(Unit); controller.connection?.close() }
    }

    @Test fun approvalListLoadsBeyondFirstFiveHundredWithoutDroppingPriorRows() = runBlocking<Unit> {
        val offsets = mutableListOf<Int>()
        val controller = controller(this) { request ->
            val offset = request.url.parameters["offset"]!!.toInt(); offsets += offset
            respond(buildJsonObject { put("items", buildJsonArray {
                repeat(if (offset == 0) 500 else 1) { index -> add(buildJsonObject { put("type", "movie"); put("id", "tt${offset + index}") }) }
            }) }.toString(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            controller.openParentTitles("2"); controller.job!!.join()
            assertEquals(500, controller.state.value.parentTitles!!.nextOffset)
            controller.refreshApprovedTitles(true); controller.job!!.join()
            assertEquals(listOf(0, 500), offsets); assertEquals(501, controller.state.value.parentTitles!!.approved.size)
            assertNull(controller.state.value.parentTitles!!.nextOffset)
        } finally { controller.connection?.close() }
    }
    @Test fun privateRevokeUsesTheExactStoredAssertionsWithoutMetadataLookup() = runBlocking<Unit> {
        val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
        fun title(imdb: String) = card("private:one").copy(metadata_origin = origin,
            identity = MediaIdentity("private:one", "movie", listOf(ExternalId("imdb", imdb))))
        val first = ApprovedTitle("private:one", "movie", metadata_origin = origin, card = title("tt1"))
        val second = first.copy(card = title("tt2"))
        var writes = 0
        val controller = controller(this) { request ->
            if (request.method == HttpMethod.Post) {
                writes++
                val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                assertEquals(Json.encodeToJsonElement(origin), body["metadata_origin"])
                assertEquals("tt1", Json.decodeFromJsonElement<Card>(body["card"]!!).identity.ids.single().value)
                respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                assertEquals("/api/profiles/2/approvals", request.url.encodedPath)
                respond(buildJsonObject { put("items", Json.encodeToJsonElement(listOf(second))) }.toString(),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        controller.mutable.value = controller.mutable.value.copy(parentTitles = ParentTitlesState("2", "Child", approved = listOf(first, second), loaded = true))
        try {
            controller.revokeParentTitle(first); controller.job!!.join()
            assertEquals(1, writes); assertEquals(listOf(second), controller.state.value.parentTitles!!.approved)
        } finally { controller.connection?.close() }
    }

    @Test fun aQueuedApprovalCannotAdoptANewerSameIdSearchResult() = runBlocking<Unit> {
        val origin = MetadataOrigin("stremio", "addon:7", "a".repeat(32))
        val old = card("private:one").copy(metadata_origin = origin, identity = MediaIdentity("private:one", "movie", listOf(ExternalId("imdb", "tt1"))))
        val next = old.copy(identity = old.identity.copy(ids = listOf(ExternalId("imdb", "tt2"))))
        var calls = 0
        val controller = controller(this) { calls++; error("No request expected") }
        controller.mutable.value = controller.mutable.value.copy(parentTitles = ParentTitlesState("2", "Child", rows = listOf(
            ParentTitleRow("row", "Addon", "stremio", 7, items = listOf(ParentTitleItem(next, true))))))
        try {
            controller.approveParentTitle("row", old); controller.job!!.join()
            assertEquals(0, calls); assertContains(controller.state.value.error.orEmpty(), "Search for the title again")
        } finally { controller.connection?.close() }
    }

}
