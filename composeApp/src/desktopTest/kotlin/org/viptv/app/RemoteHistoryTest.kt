package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.player.PlaybackInput
import org.viptv.engine.wire.*
import kotlin.test.*

class RemoteHistoryTest {
    private class MemoryStore : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    @Test fun opaqueEpisodeAndParentRemainStructuredAndLeaseIsNotMediaIdentity() = runBlocking {
        val parent = MediaIdentity("opaque-parent", "series", emptyList())
        val episode = MediaIdentity("provider:opaque:episode", "series", emptyList(), EpisodeCoordinates("aired", 2, 4))
        val payloads = mutableListOf<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Put, request.method)
            assertEquals("/api/v3/profiles/12/progress", request.url.encodedPath)
            payloads += Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond("{\"ok\":true}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", MemoryStore(), client, browserMode = false)
        val input = PlaybackInput("gateway-lease", "https://media.example/clip", profileId = "12")
        val card = Card(episode, "metadata-provider", "Episode four", badges = emptyList())
        remote.recordProgress(input, card, 12_345, null, parent)
        remote.recordProgress(input, card, 25_000, 60_000, parent, ended = true)
        val first = Json.decodeFromJsonElement<PlaybackRecord>(payloads[0].getValue("record"))
        assertEquals(episode, first.identity)
        assertEquals(parent, first.parent_identity)
        assertEquals(12_345L, first.position_millis)
        assertEquals(0L, first.duration_millis)
        assertEquals(0L, first.updated_at_millis)
        assertNull(first.completed_at_millis)
        assertEquals(parent, Json.decodeFromJsonElement<MediaIdentity>(payloads[0].getValue("parent_identity")))
        val ended = Json.decodeFromJsonElement<PlaybackRecord>(payloads[1].getValue("record"))
        assertEquals(60_000L, ended.position_millis)
        assertTrue(payloads[1].getValue("ended").jsonPrimitive.boolean)
        assertFailsWith<IllegalArgumentException> {
            remote.recordProgress(PlaybackInput("unbound", "https://media.example/clip"), card, 1, 10)
        }
        assertEquals(2, payloads.size)
        remote.close()
    }
}
