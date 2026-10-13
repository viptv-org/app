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

class LibraryRefreshTest {
    @Test fun backgroundMetadataRefreshRetainsAlreadyLoadedPagesAndUpdatesControls() = runBlocking {
        fun card(id: String) = Card(identity = MediaIdentity(id, "series", emptyList()),
            provider_id = "simkl", title = id, badges = emptyList())
        val query = defaultLibraryQuery().copy(limit = 2)
        val original = LibrarySnapshot(Scope("server", "owner", "12", 4), 4, query,
            listOf(card("a"), card("b"), card("c")), complete = true,
            availability = Availability.AVAILABLE, refresh_after_millis = 4000)
        val fresh = original.copy(cards = listOf(card("a").copy(title = "Updated"), card("b")), next_cursor = "2",
            refresh_after_millis = null, controls = listOf(QueryControl("genre", "Genre", ControlKind.SINGLECHOICE,
                false, listOf(Choice("Action", "Action")))))
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond(Json.encodeToString(fresh), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val controller = ApplicationController(this, store)
        val connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
        controller.connection = connection
        controller.mutable.value = ApplicationState(screen = Screen.Library, selectedProfile = "12",
            libraryQuery = query, libraryPage = original)
        controller.refreshLibraryPage()
        controller.job?.join()
        val page = requireNotNull(controller.state.value.libraryPage)
        assertEquals(listOf("a", "b", "c"), page.cards.map { it.identity.id })
        assertEquals("Updated", page.cards.first().title)
        assertEquals("Genre", page.controls.single().label)
        assertNull(page.next_cursor)
        assertNull(page.refresh_after_millis)
        assertEquals(1, requests)
        connection.close()
    }
}
