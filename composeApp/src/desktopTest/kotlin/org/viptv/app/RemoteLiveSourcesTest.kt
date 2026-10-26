package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import kotlin.test.*

class RemoteLiveSourcesTest {
    @Test fun liveSourceCreationUsesTheCurrentMutationRoute() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            if (request.method != HttpMethod.Post || request.url.encodedPath != "/api/profiles/1/live/channel-1/sources") {
                respond("""{"error":"method_not_allowed"}""", HttpStatusCode.MethodNotAllowed,
                    headersOf(HttpHeaders.ContentType, "application/json"))
            } else respond("""{"streams":[{"id":"stream-1","provider_id":"m3u:7","label":"Owned live channel","quality_height":1080,"audio_languages":["eng"]}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val connection = RemoteConnection("https://watch.example", store, client, browserMode = false)
        val source = connection.liveSources("1", "channel-1").single()
        assertEquals("stream-1", source.id)
        assertEquals("Owned live channel", source.label)
        assertEquals(1080L, source.quality_height)
        assertEquals(listOf("eng"), source.audio_languages)
        connection.close()
    }
}
