package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.viptv.app.components.ServerMessages
import org.viptv.app.connection.*
import kotlin.test.*

class ClientUpdateRequiredTest {
    private class Store : RemoteSessionStore {
        override suspend fun read() = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun remote(respond: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        RemoteConnection("https://watch.example", Store(),
            HttpClient(MockEngine(respond)) { configureTransport() }, browserMode = false)

    @Test fun clientUpdateRequiredMapsToTheUpdateAppMessage() = runBlocking {
        val connection = remote { request ->
            assertEquals("/api/profiles/12/preferences", request.url.encodedPath)
            respond("""{"error":"Update VIPTV to use this server's current catalog and playback APIs.","error_code":"client_update_required"}""",
                HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            val failure = assertFailsWith<RemoteFailure> { connection.profilePreferences("12") }
            assertEquals(409, failure.status)
            assertEquals("client_update_required", failure.code)
            assertEquals(ServerMessages.UPDATE_APP, failure.message)
            assertTrue(failure.updateRequired)
        } finally { connection.close() }
    }

    @Test fun otherServerHumanErrorsSurfaceTheirCopy() = runBlocking {
        val connection = remote { request ->
            respond("""{"error":"Search is not configured for this profile","error_code":"search_unavailable"}""",
                HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            val failure = assertFailsWith<RemoteFailure> { connection.profilePreferences("12") }
            assertEquals("Search is not configured for this profile", failure.message)
            assertEquals("search_unavailable", failure.code)
            assertFalse(failure.updateRequired)
        } finally { connection.close() }
    }

    @Test fun technicalServerErrorsKeepTheGenericMessage() = runBlocking {
        val connection = remote { request ->
            respond("""{"error":"endpoint /api/profiles failed with SQL error"}""",
                HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        try {
            val failure = assertFailsWith<RemoteFailure> { connection.profilePreferences("12") }
            assertEquals("The server could not complete this request.", failure.message)
        } finally { connection.close() }
    }
}