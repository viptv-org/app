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

class LivePagingFooterTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun channel(number: Int) = LiveCatalogChannel(id = "ch$number", connection_id = "conn1",
        card = Card(identity = MediaIdentity("tt$number", "channel", emptyList()), provider_id = "stremio",
            title = "Channel $number", badges = emptyList()))

    private fun livePage(offset: Int, total: Int): LiveCatalogPage {
        val end = minOf(offset + 2, total)
        return LiveCatalogPage(channels = ((offset + 1)..end).map(::channel),
            next_cursor = end.takeIf { it < total }?.toString(), complete = end >= total, availability = "Available")
    }

    private suspend fun await(what: String, predicate: () -> Boolean) {
        withTimeout(5_000) { while (!predicate()) delay(10) }
    }

    private fun initial() = LiveCatalogPage(channels = listOf(channel(1), channel(2)), next_cursor = "2", availability = "Available")
    private fun channels(controller: ApplicationController) = controller.state.value.livePage!!.channels
    private fun cursor(controller: ApplicationController) = controller.state.value.livePage!!.next_cursor

    @Test fun pagesLoadReachTheEndAndARetryRecovers() = runBlocking {
        val total = 6
        val requested = mutableListOf<Int>()
        var failNext = false
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath == "/api/profiles/owner/live/channels" -> {
                    if (failNext) { failNext = false; respondError(HttpStatusCode.InternalServerError) }
                    else {
                        val offset = requireNotNull(request.url.parameters["offset"]).toInt()
                        requested += offset
                        respond(Json.encodeToString(livePage(offset, total)),
                            headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Live, selectedProfile = "owner", livePage = initial())
        try {
            controller.moreLive()
            assertTrue(controller.state.value.busy)
            await("first page") { !controller.state.value.busy }
            assertEquals(4, channels(controller).size)
            assertEquals("4", cursor(controller))
            assertNull(controller.state.value.livePageError)

            controller.moreLive()
            assertTrue(controller.state.value.busy)
            await("end page") { !controller.state.value.busy }
            assertEquals(6, channels(controller).size)
            assertNull(cursor(controller))

            controller.mutable.value = controller.state.value.copy(livePage = initial(), liveQuery = org.viptv.app.connection.LiveQuery())
            failNext = true
            controller.moreLive()
            await("failed page") { controller.state.value.livePageError != null && !controller.state.value.busy }
            assertEquals(2, channels(controller).size)
            assertNull(controller.state.value.error)

            controller.moreLive()
            await("retried") { controller.state.value.livePageError == null && channels(controller).size == 4 }
            assertEquals(listOf(2, 4, 2), requested)
        } finally { remote.close() }
    }
}