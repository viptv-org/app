package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

class StreamedSearchTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val fast = SearchResultRow("addon:1", "Fast catalog", "stremio", 1, cards = listOf(card("addon:1")))
    private val slow = SearchResultRow("addon:2", "Slow catalog", "stremio", 2, cards = listOf(card("addon:2")))

    private fun card(provider: String) = Card(MediaIdentity("tt123", "series", emptyList()),
        provider_id = provider, title = "Same title", badges = emptyList())
    private fun loading(id: String, title: String) = SearchResultRow(id, title, "stremio", addon_id = 1, state = "Loading")

    private suspend fun until(what: String, condition: () -> Boolean) {
        try { withTimeout(5_000) { while (!condition()) delay(5) } }
        catch (_: TimeoutCancellationException) { fail("timed out waiting for $what") }
    }

    @Test fun theFastProviderRowRendersBeforeTheSlowProviderFinishes() = runBlocking {
        val slowRelease = CompletableDeferred<Unit>()
        val remote = StreamedRemote(store) { path, emit ->
            assertEquals("/api/search/stream?query=dune", path)
            emit("plan", Json.encodeToString(SearchStreamPlan(
                listOf(loading("addon:1", "Fast catalog"), loading("addon:2", "Slow catalog")))))
            emit("row", Json.encodeToString(fast))
            slowRelease.await()
            emit("row", Json.encodeToString(slow))
            emit("done", """{"complete":true}""")
        }
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "profile")
        try {
            controller.search("dune")
            until("the fast row") { controller.state.value.searchRows.any { it.id == "addon:1" && it.cards.isNotEmpty() } }
            val pending = controller.state.value.searchRows.single { it.id == "addon:2" }
            assertEquals("Loading", pending.state, "the slow row stays in its loading state")
            assertTrue(pending.cards.isEmpty())
            slowRelease.complete(Unit)
            controller.job!!.join()
            assertEquals(listOf("addon:1", "addon:2"), controller.state.value.searchRows.map { it.id }, "the plan order never changes")
            assertTrue(controller.state.value.searchRows.all { it.cards.isNotEmpty() })
            assertNull(controller.state.value.error)
        } finally { remote.close() }
    }

    @Test fun aSupersededQueryNeverShowsTheOlderStreamsRows() = runBlocking {
        var term = ""
        val firstShown = CompletableDeferred<Unit>()
        val remote = StreamedRemote(store) { _, emit ->
            if (term == "first") {
                emit("plan", Json.encodeToString(SearchStreamPlan(listOf(loading("addon:1", "First")))))
                emit("row", Json.encodeToString(fast))
                firstShown.complete(Unit)
                awaitCancellation()
            } else {
                emit("plan", Json.encodeToString(SearchStreamPlan(listOf(loading("addon:2", "Second")))))
                emit("row", Json.encodeToString(slow))
                emit("done", """{"complete":true}""")
            }
        }
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "profile")
        try {
            term = "first"
            controller.search("first")
            firstShown.await()
            assertTrue(controller.state.value.searchRows.any { it.id == "addon:1" })
            term = "second"
            controller.search("second")
            controller.job!!.join()
            assertEquals("second", controller.state.value.searchTerm)
            assertEquals(listOf("addon:2"), controller.state.value.searchRows.map { it.id })
            assertNull(controller.state.value.error)
        } finally { remote.close() }
    }

    @Test fun aMidStreamAuthorityErrorStaysOnSearchAndKeepsTheFastRow() = runBlocking {
        val remote = StreamedRemote(store) { _, emit ->
            emit("plan", Json.encodeToString(SearchStreamPlan(
                listOf(loading("addon:1", "Fast catalog"), loading("addon:2", "Slow catalog")))))
            emit("row", Json.encodeToString(fast))
            emit("error", """{"status":409,"error":"The configuration changed. Search again."}""")
        }
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "profile")
        try {
            controller.search("dune"); controller.job!!.join()
            assertEquals("The configuration changed. Search again.", controller.state.value.error)
            assertEquals(Screen.Search, controller.state.value.screen)
            assertTrue(controller.state.value.searchRows.any { it.id == "addon:1" && it.cards.isNotEmpty() })
            assertEquals("Loading", controller.state.value.searchRows.single { it.id == "addon:2" }.state)
        } finally { remote.close() }
    }

    @Test fun aRawServerErrorStringNeverReachesTheUi() = runBlocking {
        val remote = StreamedRemote(store) { _, emit ->
            emit("error", """{"status":500,"error":"internal: GET /api/search panicked at src/main.rs:42"}""")
        }
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "profile")
        try {
            controller.search("dune"); controller.job!!.join()
            assertEquals("The search could not finish. Try again.", controller.state.value.error)
        } finally { remote.close() }
    }

    @Test fun aServerWithoutTheStreamFallsBackToTheSnapshotEndpoint() = runBlocking {
        val snapshot = SearchSnapshot(listOf(fast), true)
        val remote = StreamedRemote(store, snapshot = Json.encodeToString(snapshot)) { path, _ ->
            assertEquals("/api/search/stream?query=dune", path)
            throw SearchStreamUnavailable()
        }
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "profile")
        try {
            controller.search("dune"); controller.job!!.join()
            assertEquals(listOf(fast), controller.state.value.searchRows)
            assertEquals(listOf("addon:1"), controller.state.value.results.map { it.provider_id })
            assertNull(controller.state.value.error)
        } finally { remote.close() }
    }

    @Test fun aTimedOutSearchKeepsWhatWasShownAndRetryReplacesTheFailure() = runBlocking {
        var timedOut = false
        val remote = StreamedRemote(store) { _, emit ->
            if (timedOut) throw HttpRequestTimeoutException(HttpRequestBuilder())
            emit("plan", Json.encodeToString(SearchStreamPlan(listOf(loading("addon:1", "Fast catalog")))))
            emit("row", Json.encodeToString(fast))
            emit("done", """{"complete":true}""")
        }
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "profile")
        try {
            controller.search("dune"); controller.job!!.join()
            assertEquals(listOf(fast), controller.state.value.searchRows)
            assertNull(controller.state.value.error)
            timedOut = true
            controller.retrySearch(); controller.job!!.join()
            assertEquals(listOf(fast), controller.state.value.searchRows, "a failed retry must not discard the rows already shown")
            assertEquals("Your server took too long to answer this search. Try again.", controller.state.value.error)
            timedOut = false
            controller.retrySearch(); controller.job!!.join()
            assertNull(controller.state.value.error, "a retry that answers clears the failure")
            assertEquals(listOf(fast), controller.state.value.searchRows)
        } finally { remote.close() }
    }

    @Test fun aSearchStreamThatNeverFinishesShowsTheDeadlineFailureAndKeepsShownRows() = runBlocking {
        var hang = true
        val remote = StreamedRemote(store) { _, emit ->
            emit("plan", Json.encodeToString(SearchStreamPlan(listOf(loading("addon:1", "Fast catalog")))))
            emit("row", Json.encodeToString(fast))
            if (hang) awaitCancellation() else emit("done", """{"complete":true}""")
        }
        val controller = ApplicationController(this, store, searchStreamTimeoutMillis = 150)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "profile")
        try {
            controller.search("dune")
            until("the fast row") { controller.state.value.searchRows.any { it.id == "addon:1" && it.cards.isNotEmpty() } }
            until("the deadline failure") { controller.state.value.error == "Your server took too long to answer this search. Try again." }
            controller.job!!.join()
            assertTrue(controller.state.value.searchRows.any { it.id == "addon:1" && it.cards.isNotEmpty() }, "rows already shown survive the deadline")
            assertFalse(controller.state.value.busy, "the deadline settles the search rather than leaving it busy")
            hang = false
            controller.retrySearch(); controller.job!!.join()
            assertNull(controller.state.value.error, "a retry that completes clears the deadline failure")
            assertTrue(controller.state.value.searchRows.any { it.id == "addon:1" && it.cards.isNotEmpty() })
        } finally { remote.close() }
    }

    @Test fun aSearchThatFailsAfterTheViewerLeftPublishesNoErrorOnTheScreenTheyMovedTo() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val remote = StreamedRemote(store) { _, emit ->
            emit("plan", Json.encodeToString(SearchStreamPlan(listOf(loading("addon:1", "Fast catalog")))))
            release.await()
            throw HttpRequestTimeoutException(HttpRequestBuilder())
        }
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "profile")
        try {
            controller.search("dune")
            while (controller.job?.isActive != true) yield()
            delay(400)
            controller.mutable.value = controller.state.value.copy(screen = Screen.Home)
            release.complete(Unit)
            controller.job!!.join()
            assertNull(controller.state.value.error, "an abandoned search must not paint a connection error on Home")
            assertEquals(Screen.Home, controller.state.value.screen)
        } finally { remote.close() }
    }

    @Test fun aServerFailureStaysOnTheSearchScreenAndRemainsRetryable() = runBlocking {
        val remote = StreamedRemote(store) { _, emit ->
            emit("error", """{"status":500,"error":"The server could not complete this request."}""")
        }
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "profile")
        try {
            controller.search("dune"); controller.job!!.join()
            assertEquals("The server could not complete this request.", controller.state.value.error)
            assertEquals(Screen.Search, controller.state.value.screen)
            controller.retrySearch()
            assertTrue(controller.job?.isActive == true, "retrying a failed search issues another request for the same term")
            controller.job?.join()
            assertEquals("The server could not complete this request.", controller.state.value.error)
        } finally { remote.close() }
    }
}

private class StreamedRemote(
    store: RemoteSessionStore,
    snapshot: String? = null,
    private val stream: suspend (path: String, emit: suspend (String?, String?) -> Unit) -> Unit,
) : RemoteConnection("https://watch.example", store,
    client = HttpClient(MockEngine { request ->
        if (request.url.encodedPath == "/api/search" && snapshot != null) {
            respond(snapshot, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        } else respond("{}", HttpStatusCode.NotFound, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }),
    browserMode = false) {
    override suspend fun searchEventStream(path: String, onEvent: suspend (String?, String?) -> Unit) = stream(path, onEvent)
}