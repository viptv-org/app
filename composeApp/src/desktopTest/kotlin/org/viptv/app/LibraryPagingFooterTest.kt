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

class LibraryPagingFooterTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun card(number: Long) = Card(identity = MediaIdentity("tt$number", "movie", emptyList()),
        provider_id = "stremio", title = "Movie $number", badges = emptyList())

    private suspend fun await(what: String, predicate: () -> Boolean) {
        withTimeout(5_000) { while (!predicate()) delay(10) }
    }

    @Test fun aFailedPageKeepsCardsAndRetryRecovers() = runBlocking {
        val requested = EngineRequests<Long>()
        var failNext = false
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.encodedPath != "/api/profiles/owner/library" -> respondError(HttpStatusCode.NotFound)
                failNext -> { failNext = false; respondError(HttpStatusCode.InternalServerError) }
                else -> {
                    val offset = requireNotNull(request.url.parameters["offset"]).toLong()
                    requested += offset
                    val end = minOf(offset + 2L, 6L)
                    respond(Json.encodeToString(LibrarySnapshot(
                        scope = Scope("server", "1", "owner", 1), revision = 1,
                        query = defaultLibraryQuery().copy(offset = offset),
                        cards = ((offset + 1)..end).map(::card),
                        next_cursor = end.takeIf { it < 6L }?.toString(), complete = end >= 6L,
                        availability = Availability.AVAILABLE)),
                        headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Library, selectedProfile = "owner")
        try {
            controller.libraryQuery(defaultLibraryQuery())
            await("first page") { controller.state.value.libraryPage?.cards?.isNotEmpty() == true && !controller.state.value.busy }
            assertEquals(2, controller.state.value.libraryPage!!.cards.size)
            assertEquals("2", controller.state.value.libraryPage!!.next_cursor)
            assertNull(controller.state.value.libraryPageError)

            failNext = true
            controller.libraryMore()
            await("failed page") { controller.state.value.libraryPageError != null && !controller.state.value.busy }
            assertEquals(2, controller.state.value.libraryPage!!.cards.size)
            assertNull(controller.state.value.error)

            controller.libraryMore()
            await("retried") { controller.state.value.libraryPageError == null &&
                controller.state.value.libraryPage!!.cards.size == 4 }
            assertEquals(listOf(0L, 2L), requested)
        } finally { remote.close() }
    }
}