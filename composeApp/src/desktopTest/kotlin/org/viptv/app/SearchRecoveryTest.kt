package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

class SearchRecoveryTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }
    private val card = Card(MediaIdentity("tt1160419", "movie", emptyList()), provider_id = "addon:1", title = "Dune", badges = emptyList())
    private val found = SearchSnapshot(listOf(SearchResultRow("addon:1", "Cinemeta", "stremio", 1, cards = listOf(card))), true)

    @Test fun aTimedOutSearchKeepsWhatWasShownAndRetryReplacesTheFailure() = runBlocking {
        var timeout = false
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/search", request.url.encodedPath)
            if (timeout) throw HttpRequestTimeoutException(HttpRequestBuilder())
            respond(Json.encodeToString(found), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Search, selectedProfile = "profile")
        try {
            controller.search("dune"); controller.job!!.join()
            assertEquals(listOf(card), controller.state.value.results)
            assertNull(controller.state.value.error)
            timeout = true
            controller.retrySearch(); controller.job!!.join()
            assertEquals(listOf(card), controller.state.value.results, "a failed retry must not discard the results already shown")
            assertEquals("Your server took too long to answer this search. Try again.", controller.state.value.error)
            timeout = false
            controller.retrySearch(); controller.job!!.join()
            assertNull(controller.state.value.error, "a retry that answers clears the failure")
            assertEquals(listOf(card), controller.state.value.results)
        } finally { remote.close() }
    }

    @Test fun aSearchThatFailsAfterTheViewerLeftPublishesNoErrorOnTheScreenTheyMovedTo() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine {
            release.await()
            throw HttpRequestTimeoutException(HttpRequestBuilder())
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
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
        val client = HttpClient(MockEngine {
            respond("""{"error":"boom"}""", HttpStatusCode.InternalServerError, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
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
