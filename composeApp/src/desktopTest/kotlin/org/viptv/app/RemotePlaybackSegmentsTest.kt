package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import org.viptv.app.features.player.TvSkipKind
import org.viptv.app.features.player.TvSkipRange
import kotlin.test.*

class RemotePlaybackSegmentsTest {
    private class Store : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) { }
        override suspend fun clear() { }
    }

    private fun connection(calls: MutableList<String>, body: () -> Pair<HttpStatusCode, String>): RemoteConnection {
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            calls += "${request.method.value} $path"
            val (status, text) = when (path) {
                "/api/auth/login" -> HttpStatusCode.OK to """{"csrf_token":"fixture-csrf"}"""
                "/api/bootstrap" -> HttpStatusCode.OK to """{"identity":{"profiles":[],"profile_id":null}}"""
                else -> body()
            }
            respond(text, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        return RemoteConnection("https://watch.example", Store(), client, browserMode = true)
    }

    @Test fun segmentsAreReadOnceFromThePlaybackAndKeepTheirKinds() = runBlocking {
        val calls = mutableListOf<String>()
        val connection = connection(calls) {
            HttpStatusCode.OK to """{"segments":[{"kind":"intro","start_ms":310571,"end_ms":400571},""" +
                """{"kind":"credits","start_ms":1290000,"end_ms":1380000}]}"""
        }
        connection.login("fixture", "disposable")
        assertEquals(listOf(TvSkipRange(310571, 400571, TvSkipKind.Intro), TvSkipRange(1290000, 1380000, TvSkipKind.Credits)),
            connection.playbackSegments("lease"))
        assertEquals(1, calls.count { it == "GET /api/playback/lease/segments" })
        connection.close()
    }

    @Test fun anEmptyAnswerLeavesNothingToSkip() = runBlocking {
        val connection = connection(mutableListOf()) { HttpStatusCode.OK to """{"segments":[]}""" }
        connection.login("fixture", "disposable")
        assertTrue(connection.playbackSegments("lease").isEmpty())
        connection.close()
    }

    @Test fun aServerWithoutTheRouteHidesTheFeature() = runBlocking {
        val connection = connection(mutableListOf()) { HttpStatusCode.NotFound to """{"error_code":"not_found"}""" }
        connection.login("fixture", "disposable")
        assertTrue(connection.playbackSegments("lease").isEmpty())
        connection.close()
    }

    @Test fun aFailedLookupOrAnUnknownKindIsNoSegment() = runBlocking {
        val failed = connection(mutableListOf()) { HttpStatusCode.InternalServerError to "{}" }
        failed.login("fixture", "disposable")
        assertTrue(failed.playbackSegments("lease").isEmpty())
        failed.close()
        val mixed = connection(mutableListOf()) {
            HttpStatusCode.OK to """{"segments":[{"kind":"mixed-op","start_ms":0,"end_ms":1000},""" +
                """{"kind":"recap","start_ms":5000,"end_ms":5000},{"kind":"recap","start_ms":0,"end_ms":30000}]}"""
        }
        mixed.login("fixture", "disposable")
        assertEquals(listOf(TvSkipRange(0, 30000, TvSkipKind.Recap)), mixed.playbackSegments("lease"))
        mixed.close()
    }
}
