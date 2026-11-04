package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import org.viptv.app.state.*
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import kotlin.test.*

class BackRestoresListStateTest {
    private class MemoryStore(var connection: SavedConnection = SavedConnection.Remote(SavedRemoteSession("https://fixture.example", "r1"))) : RemoteSessionStore {
        override suspend fun read() = (connection as? SavedConnection.Remote)?.session
        override suspend fun savedConnection() = connection
        override suspend fun save(session: SavedRemoteSession) { connection = SavedConnection.Remote(session) }
        override suspend fun clear() { connection = SavedConnection.None }
    }

    private fun card(id: String, title: String) =
        """{"identity":{"id":"$id","kind":"movie","ids":[]},"provider_id":"addon:1","title":"$title","badges":[]}"""

    private fun cards() = (1..3).joinToString(",") { card("browse-$it", "Browse $it") }

    private fun client(requests: MutableList<String>) = HttpClient(MockEngine { request ->
        val path = request.url.encodedPath
        requests += path
        val body = when {
            path == "/api/auth/device/refresh" -> """{"access_token":"a2","refresh_token":"r2"}"""
            path == "/api/bootstrap" -> """{"identity":{"profiles":[{"id":"p1","name":"Member"}],"profile_id":"p1"},"home":null}"""
            path == "/api/discovery-controls" ->
                """{"provider_id":"stremio","media_types":["movie"],"controls":[],
                    "catalogs":[{"id":"top","name":"Top","type":"movie"}],"discovery":{"availability":"Available"},
                    "search":{"availability":"Available"},"filters":{"availability":"Available"},
                    "calendar":{"availability":"Unsupported"}}"""
            path == "/api/discover" -> """{"cards":[${cards()}],"complete":true}"""
            path.startsWith("/api/meta/") -> """{"item":${card("browse-1", "Browse 1")},"episodes":[]}"""
            path.startsWith("/api/profiles/p1/library") ->
                """{"scope":{"connection_id":"c","account_id":"a","profile_id":"p1","revision":1},"revision":1,
                    "query":{"source":"favorites","offset":0,"limit":100,"sort":"last_watched","show":"unwatched","type":"all"},
                    "cards":[${card("browse-1", "Browse 1")}],"complete":true,"availability":"Available","sources":[],"controls":[],"statistics":[]}"""
            path.startsWith("/api/calendar") -> """{"entries":[],"complete":true}"""
            else -> """{}"""
        }
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }) { configureTransport() }

    private val opened = Card(MediaIdentity("browse-1", "movie", emptyList()), provider_id = "addon:1", title = "Browse 1", badges = emptyList())

    private data class Case(
        val name: String,
        val screen: Screen,
        val listPrefixes: List<String>,
        val arrange: (ApplicationController) -> Unit,
        val assert: (ApplicationState) -> Unit,
    )

    private val cases = listOf(
        Case("Browse", Screen.Discover, listOf("/api/discover"),
            arrange = { it.browseFilter("genre", "Comedy") },
            assert = { assertEquals("Comedy", it.browseQuery.genre, "Browse restores its genre filter") }),
        Case("Library", Screen.Library, listOf("/api/profiles/p1/library"),
            arrange = { it.libraryQuery(org.viptv.app.connection.defaultLibraryQuery().copy(show = "unwatched")) },
            assert = { assertEquals("unwatched", it.libraryQuery.show, "My List restores its show filter") }),
        Case("Calendar", Screen.Calendar, listOf("/api/calendar"),
            arrange = { it.calendarCategory("series") },
            assert = { assertEquals("series", it.calendarType, "Calendar restores its chip") }),
        Case("Search", Screen.Search, emptyList(),
            arrange = { it.mutable.value = it.state.value.copy(searchTerm = "monster"); it.searchFilters(setOf("Movies")) },
            assert = { assertEquals("monster", it.searchTerm); assertEquals(setOf("Movies"), it.searchFilters, "Search restores its filters") }),
    )

    @Test fun everyFilterScreenRestoresItsFiltersAndDoesNotReload() = runBlocking<Unit> {
        for (case in cases) {
            val requests = mutableListOf<String>()
            val store = MemoryStore()
            val controller = ApplicationController(this, store, remoteFactory = { origin, storage ->
                RemoteConnection(origin, storage, client(requests), browserMode = false)
            })
            try {
                controller.restore(); requireNotNull(controller.job).join()
                assertEquals(Screen.Home, controller.state.value.screen, "${case.name}: boot lands on Home")
                controller.navigate(case.screen)
                requireNotNull(controller.job).join()
                case.arrange(controller)
                requireNotNull(controller.job).join()
                val before = case.listPrefixes.sumOf { prefix -> requests.count { it.startsWith(prefix) } }
                controller.open(opened)
                requireNotNull(controller.job).join()
                assertEquals(Screen.Details, controller.state.value.screen, "${case.name}: the title opens")
                controller.navigateBack()
                requireNotNull(controller.job).join()
                assertEquals(case.screen, controller.state.value.screen, "${case.name}: Back returns to the list")
                case.assert(controller.state.value)
                val after = case.listPrefixes.sumOf { prefix -> requests.count { it.startsWith(prefix) } }
                assertEquals(before, after, "${case.name}: Back must not reload the list")
            } finally { controller.disconnect() }
        }
    }
}