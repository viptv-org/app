package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class MyListWriteKeepsLoadedPagesTest {
    private fun card(number: Int) = Card(identity = MediaIdentity("tt%07d".format(number), "movie", emptyList()),
        provider_id = "library", title = "Movie %02d".format(number), badges = emptyList())

    private class Host(var cards: List<Card>) : AutoCloseable {
        val reads = CopyOnWriteArrayList<String>()
        val deletes = CopyOnWriteArrayList<String>()
        private val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        private fun MockRequestHandleScope.json(text: String) =
            respond(text, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

        private val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/api/profiles/1/library/membership" ->
                    json(Json.encodeToString(LibraryMembershipSnapshot(cards.map { it.identity }, 1, complete = true)))
                path.startsWith("/api/profiles/1/library/") && request.method == HttpMethod.Delete -> {
                    val id = path.removePrefix("/api/profiles/1/library/")
                    deletes += id
                    cards = cards.filterNot { "${it.identity.kind}/${it.identity.id}" == id }
                    json("""{"ok":true}""")
                }
                path == "/api/profiles/1/library" -> {
                    val offset = request.url.parameters["offset"]?.toLong() ?: 0L
                    val limit = request.url.parameters["limit"]?.toLong() ?: 100L
                    reads += "$offset+$limit"
                    val page = cards.drop(offset.toInt()).take(limit.toInt())
                    val end = offset + page.size
                    json(Json.encodeToString(LibrarySnapshot(Scope("viptv:remote", "account", "1", 1), 1,
                        defaultLibraryQuery().copy(offset = offset, limit = limit), page,
                        next_cursor = end.takeIf { it < cards.size }?.toString(), complete = end >= cards.size,
                        availability = Availability.AVAILABLE)))
                }
                else -> respond("""{"error":"not in this fixture"}""", HttpStatusCode.NotFound)
            }
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val sessionStore: RemoteSessionStore get() = store
        override fun close() = remote.close()
    }

    private fun CoroutineScope.controller(host: Host) = ApplicationController(this, host.sessionStore).also {
        it.connection = host.remote
        it.mutable.value = ApplicationState(screen = Screen.Library, selectedProfile = "1",
            profiles = listOf(Profile("1", "QA", null)), favoriteKeys = host.cards.map { card -> card.identity.kind to card.identity.id }.toSet(),
            preferences = PreferencesSnapshot())
    }

    private suspend fun until(what: String, condition: () -> Boolean) =
        withTimeout(5_000) { while (!condition()) delay(10) }.also { assertTrue(condition(), what) }

    private suspend fun ApplicationController.loadTwoPages() {
        libraryQuery(defaultLibraryQuery().copy(limit = 2))
        until("the first page") { state.value.libraryPage?.cards?.size == 2 && !state.value.busy }
        libraryMore()
        until("the second page") { state.value.libraryPage?.cards?.size == 4 && !state.value.busy }
    }

    private fun titles(controller: ApplicationController) = controller.state.value.libraryPage?.cards?.map { it.title }

    @Test fun removingADeepTitleKeepsThePagesAlreadyLoaded() = runBlocking<Unit> {
        val cards = (1..4).map(::card)
        Host(cards).use { host ->
            val controller = controller(host)
            controller.loadTwoPages()
            assertEquals(listOf("Movie 01", "Movie 02", "Movie 03", "Movie 04"), titles(controller))

            controller.removeFromMyList(cards[0])
            controller.job?.join()

            assertEquals(listOf("Movie 02", "Movie 03", "Movie 04"), titles(controller),
                "the loaded pages stay; only the removed title leaves")
            assertEquals(listOf("movie/tt0000001"), host.deletes.toList(), "exactly one DELETE")
            assertEquals(listOf("Movie 02", "Movie 03", "Movie 04"), controller.state.value.library.map { it.title })
        }
    }

    @Test fun savingFromACardKeepsThePagesAlreadyLoaded() = runBlocking<Unit> {
        val cards = (1..4).map(::card)
        Host(cards).use { host ->
            val controller = controller(host)
            controller.loadTwoPages()

            controller.toggleFavorite(cards[1])
            controller.job?.join()

            assertEquals(listOf("Movie 01", "Movie 03", "Movie 04"), titles(controller),
                "the star's removal keeps the loaded pages too")
            assertEquals(listOf("movie/tt0000002"), host.deletes.toList(), "exactly one DELETE")
        }
    }

    @Test fun aFilterChangeStartsFromTheFirstPageAgain() = runBlocking<Unit> {
        val cards = (1..4).map(::card)
        Host(cards).use { host ->
            val controller = controller(host)
            controller.loadTwoPages()

            controller.libraryQuery(defaultLibraryQuery().copy(limit = 2, type = "movie"))
            until("the filtered first page") { controller.state.value.libraryPage?.cards?.size == 2 && !controller.state.value.busy }

            assertEquals(listOf("Movie 01", "Movie 02"), titles(controller), "a new query is a new list, not a merge")
            assertEquals("2", controller.state.value.libraryPage?.next_cursor)
        }
    }
}