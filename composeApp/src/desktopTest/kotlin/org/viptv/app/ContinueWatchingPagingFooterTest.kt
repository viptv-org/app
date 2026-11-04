package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import kotlin.test.*

class ContinueWatchingPagingFooterTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun identity(number: Int) = MediaIdentity("tt$number", "movie", emptyList())
    private fun card(number: Int) = Card(identity = identity(number), provider_id = "stremio", title = "Movie $number", badges = emptyList())
    private fun entry(number: Int) = ContinuationEntry(card = card(number),
        record = PlaybackRecord(identity = identity(number), provider_id = "stremio", title = "Movie $number",
            position_millis = 0, duration_millis = 0, updated_at_millis = 0), status = ContinuationStatus.RESUME)

    private suspend fun await(what: String, predicate: () -> Boolean) {
        withTimeout(5_000) { while (!predicate()) delay(10) }
    }

    private fun initial() = ContinueWatchingList("owner", listOf(card(1), card(2)), next = "2")
    private fun cards(controller: ApplicationController) = controller.state.value.continueWatching!!.cards
    private fun cursor(controller: ApplicationController) = controller.state.value.continueWatching!!.next

    @Test fun pagesLoadReachTheEndAndARetryRecovers() = runBlocking {
        val total = 6
        val requested = mutableListOf<Int>()
        var failNext = false
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath == "/api/profiles/owner/continue" -> {
                    if (failNext) { failNext = false; respondError(HttpStatusCode.InternalServerError) }
                    else {
                        val offset = requireNotNull(request.url.parameters["offset"]).toInt()
                        requested += offset
                        val end = minOf(offset + 2, total)
                        respond(Json.encodeToString(ContinuationPage(scope = Scope("server", "1", "owner", 1), revision = 1,
                            entries = ((offset + 1)..end).map(::entry), next_cursor = end.takeIf { it < total }?.toString(),
                            complete = end >= total)),
                            headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.ContinueWatching, selectedProfile = "owner", continueWatching = initial())
        try {
            controller.continueWatchingMore()
            assertTrue(controller.state.value.busy)
            await("first page") { !controller.state.value.busy }
            assertEquals(4, cards(controller).size)
            assertEquals("4", cursor(controller))
            assertNull(controller.state.value.continueWatchingPageError)

            controller.continueWatchingMore()
            assertTrue(controller.state.value.busy)
            await("end page") { !controller.state.value.busy }
            assertEquals(6, cards(controller).size)
            assertNull(cursor(controller))

            controller.mutable.value = controller.state.value.copy(continueWatching = initial())
            failNext = true
            controller.continueWatchingMore()
            await("failed page") { controller.state.value.continueWatchingPageError != null && !controller.state.value.busy }
            assertEquals(2, cards(controller).size)
            assertEquals("2", cursor(controller))
            assertNull(controller.state.value.error)

            controller.continueWatchingMore()
            await("retried") { controller.state.value.continueWatchingPageError == null && cards(controller).size == 4 }
            assertEquals(listOf(2, 4, 2), requested)
        } finally { remote.close() }
    }
}