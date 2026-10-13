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

class RemotePlaybackEventsTest {
    private class MemoryStore : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    @Test fun attemptEventsKeepOpaqueIdentityAndAcceptServerCompletionPolicy() = runBlocking {
        val requests = mutableListOf<JsonObject>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v3/profiles/12/playback-events", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            requests += body
            val response = when (requests.size) {
                1 -> """{"state":"recorded","provider_id":"fixture-tracker","action":"start","completed":false}"""
                2 -> """{"state":"recorded","provider_id":"fixture-tracker","action":"pause","completed":false}"""
                else -> """{"state":"recorded"}"""
            }
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", MemoryStore(), client, browserMode = false)
        val parent = MediaIdentity("opaque-title", "series", emptyList())
        val identity = MediaIdentity("opaque-episode", "series", emptyList(), EpisodeCoordinates("aired", 2, 4))
        val events = RemotePlaybackEvents(remote, PlaybackInput("actual-attempt", "https://media.example/clip", profileId = "12"),
            Card(identity = identity, provider_id = "metadata-provider", title = "Episode", badges = emptyList()), parent)
        assertEquals("fixture-tracker", events.emit(PlaybackEvent.Start, 0, 100_000).providerId)
        val stopped = events.emit(PlaybackEvent.Stop, 80_000, 100_000)
        assertEquals("pause", stopped.action)
        assertFalse(stopped.completed)
        assertEquals(listOf(1L, 2L), requests.map { it.getValue("sequence").jsonPrimitive.long })
        assertTrue(requests.all { it.getValue("attempt_id").jsonPrimitive.content == "actual-attempt" })
        assertEquals(identity, Json.decodeFromJsonElement<MediaIdentity>(requests[1].getValue("identity")))
        assertEquals(parent, Json.decodeFromJsonElement<MediaIdentity>(requests[1].getValue("parent_identity")))
        assertFailsWith<RemoteFailure> { events.emit(PlaybackEvent.Start, 80_000, 100_000) }
        remote.close()
    }
}
