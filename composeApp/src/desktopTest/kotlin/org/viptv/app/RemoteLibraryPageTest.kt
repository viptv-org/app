package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.viptv.app.connection.*
import org.viptv.engine.wire.*
import kotlin.test.*

class RemoteLibraryPageTest {
    @Test fun oneQueryResponseKeepsControlsStatisticsAndEscapesGenre() = runBlocking {
        val query = defaultLibraryQuery().copy(sort = "name_desc", genre = "Sci-Fi & Fantasy")
        val snapshot = LibrarySnapshot(scope = Scope("server", "owner", "12", 4), revision = 4,
            query = query, cards = emptyList(), complete = true, availability = Availability.AVAILABLE,
            controls = listOf(QueryControl("sort", "Sort by", ControlKind.SINGLECHOICE, false, listOf(Choice("name_desc", "Name Z–A")))),
            statistics = listOf(LibraryStatistics(MediaIdentity("title", "movie", emptyList()), watched = false)))
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals("/api/v3/profiles/12/library", request.url.encodedPath)
            assertEquals("Sci-Fi & Fantasy", request.url.parameters["genre"])
            assertEquals("name_desc", request.url.parameters["sort"])
            respond(Json.encodeToString(snapshot), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val result = connection.libraryPage("12", query)
        assertEquals(1, requests)
        assertEquals("Sort by", result.controls.single().label)
        assertNull(result.statistics.single().times_watched)
        assertNull(result.statistics.single().user_rating)
        connection.close()
    }
}
