package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import kotlin.test.*

class RemotePreferencesTest {
    @Test fun defaultFieldsAreSentWithTheDisplayedRevision() = runBlocking {
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v3/profiles/12/preferences", request.url.encodedPath)
            assertEquals(HttpMethod.Put, request.method)
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals(7, body.getValue("revision").jsonPrimitive.int)
            val preferences = body.getValue("preferences").jsonObject
            assertTrue(preferences.getValue("subtitles_enabled").jsonPrimitive.boolean)
            assertEquals("ask", preferences.getValue("source_selection").jsonPrimitive.content)
            assertEquals("fr", preferences.getValue("preferred_audio_language").jsonPrimitive.content)
            respond(buildJsonObject { put("revision", 8); put("preferences", preferences) }.toString(),
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            val result = remote.updateProfilePreferences("12", 7, ProfilePreferences(preferred_audio_language = "fr"))
            assertEquals(8, result.revision)
            assertEquals("fr", result.preferences.preferred_audio_language)
        } finally { remote.close() }
    }
}
