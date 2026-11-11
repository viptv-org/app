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

class HomeRowPagingTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun card(number: Int) = Card(identity = MediaIdentity("tt$number", "movie", emptyList()),
        provider_id = "stremio", title = "Movie $number", badges = emptyList())

    private fun home(cards: List<Card>, next: String?) = HomeSnapshot(
        scope = Scope("server", "1", "owner", 1), revision = 1,
        rows = listOf(HomeRow(
            id = "new", title = "New this week", provider_id = "stremio", state = RowState.READY,
            cards = cards, next_cursor = next,
            browse = HomeBrowseAction(HomeBrowseTarget.DISCOVER, listOf(
                QueryValue("type", listOf("movie")), QueryValue("catalog", listOf("popular")))))),
        complete = true)

    private suspend fun await(what: String, predicate: () -> Boolean) {
        withTimeout(5_000) { while (!predicate()) delay(10) }
    }

    private fun rowCards(controller: ApplicationController) = controller.state.value.home!!.rows.first().cards
    private fun rowCursor(controller: ApplicationController) = controller.state.value.home!!.rows.first().next_cursor

    @Test fun pagesOnceInOrderWithoutDuplicatesOrSkips() = runBlocking {
        val total = 48
        val requested = EngineRequests<Int>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/discover", request.url.encodedPath)
            assertEquals("movie", request.url.parameters["type"])
            assertEquals("popular", request.url.parameters["catalog"])
            val skip = requireNotNull(request.url.parameters["skip"]).toInt()
            requested += skip
            val end = minOf(skip + 12, total)
            respond(Json.encodeToString(CatalogPage(cards = ((skip + 1)..end).map(::card),
                next_cursor = end.takeIf { it < total }?.toString(), complete = end >= total)),
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "owner", home = home((1..12).map(::card), next = "12"))
        try {
            controller.homeRowPage("new")
            await("first page") { rowCards(controller).size == 24 }
            assertEquals(24, rowCards(controller).size)
            assertEquals("24", rowCursor(controller))
            while (rowCursor(controller) != null) {
                val before = rowCards(controller).size
                controller.homeRowPage("new")
                await("page after $before") { rowCards(controller).size > before }
            }
            val ids = rowCards(controller).map { it.identity.id }
            assertEquals((1..total).map { "tt$it" }, ids)
            assertEquals(ids, ids.distinct())
            assertEquals(listOf(12, 24, 36), requested)
            assertNull(controller.state.value.homeRowErrors["new"])
            assertTrue(controller.state.value.homeRowBusy.isEmpty())
        } finally { remote.close() }
    }

    @Test fun overlappingPageIsDedupedAndANonAdvancingCursorStops() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            val skip = requireNotNull(request.url.parameters["skip"]).toInt()
            // The server overlaps the previous page and reports a cursor that does not advance.
            val start = (skip + 1).coerceAtMost(10)
            val end = minOf(skip + 12, 20)
            val cards = if (start <= end) (start..end).map(::card) else emptyList()
            respond(Json.encodeToString(CatalogPage(cards = cards, next_cursor = "0", complete = false)),
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "owner", home = home((1..12).map(::card), next = "12"))
        try {
            controller.homeRowPage("new")
            await("settle") { controller.state.value.homeRowBusy.isEmpty() }
            val ids = rowCards(controller).map { it.identity.id }
            assertEquals(ids, ids.distinct())
            assertNull(rowCursor(controller))
            assertTrue(rowCards(controller).size <= 20)
        } finally { remote.close() }
    }

    @Test fun failedPageKeepsLoadedCardsAndRetryAdvances() = runBlocking {
        var secondPageRequests = 0
        val total = 36
        val client = HttpClient(MockEngine { request ->
            val skip = requireNotNull(request.url.parameters["skip"]).toInt()
            if (skip == 12) {
                secondPageRequests++
                if (secondPageRequests == 1) return@MockEngine respond("""{"error":"boom"}""", HttpStatusCode.BadGateway,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
            val end = minOf(skip + 12, total)
            respond(Json.encodeToString(CatalogPage(cards = ((skip + 1)..end).map(::card),
                next_cursor = end.takeIf { it < total }?.toString(), complete = end >= total)),
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val controller = ApplicationController(this, store)
        controller.connection = remote
        controller.mutable.value = ApplicationState(screen = Screen.Home, selectedProfile = "owner", home = home((1..12).map(::card), next = "12"))
        try {
            controller.homeRowPage("new")
            await("failure") { controller.state.value.homeRowErrors["new"] != null }
            assertEquals(12, rowCards(controller).size)
            assertEquals("12", rowCursor(controller))
            assertEquals("Could not load more titles. Try again.", controller.state.value.homeRowErrors["new"])

            controller.homeRowPage("new")
            await("retry") { rowCards(controller).size == 24 }
            assertNull(controller.state.value.homeRowErrors["new"])
            assertEquals(2, secondPageRequests)

            controller.homeRowPage("new")
            await("final page") { rowCursor(controller) == null }
            assertEquals((1..total).map { "tt$it" }, rowCards(controller).map { it.identity.id })
        } finally { remote.close() }
    }
}