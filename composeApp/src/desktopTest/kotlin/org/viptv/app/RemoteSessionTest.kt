package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteSessionTest {
    private class MemoryStore : RemoteSessionStore {
        var saved: SavedRemoteSession? = null
        override suspend fun read() = saved
        override suspend fun save(session: SavedRemoteSession) { saved = session }
        override suspend fun clear() { saved = null }
    }

    @Test
    fun deviceSessionRotatesOnExpiryAndSurvivesReopen() = runBlocking {
        val store = MemoryStore()
        var refreshes = 0
        var expired = false
        var logout = false
        fun client() = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val authorization = request.headers[HttpHeaders.Authorization]
            val (body, code) = when (path) {
                "/api/auth/device/login" -> """{"access_token":"a1","refresh_token":"r1","csrf_token":"c1"}""" to HttpStatusCode.OK
                "/api/auth/device/refresh" -> {
                    refreshes++
                    """{"access_token":"a${refreshes + 1}","refresh_token":"r${refreshes + 1}"}""" to HttpStatusCode.OK
                }
                "/api/v3/bootstrap" -> {
                    if (authorization == "Bearer a1" && expired) "{}" to HttpStatusCode.Unauthorized
                    else {
                        assertEquals("Bearer a${refreshes + 1}", authorization)
                        """{"identity":{"profiles":[],"profile_id":null},"home":null}""" to HttpStatusCode.OK
                    }
                }
                "/api/auth/logout" -> {
                    assertEquals("Bearer a${refreshes + 1}", authorization)
                    logout = true
                    """{"ok":true}""" to HttpStatusCode.OK
                }
                else -> error("Unexpected request $path")
            }
            respond(body, code, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        RemoteConnection("https://watch.example", store, client()).apply {
            login("member", "password")
            assertEquals("r1", store.saved?.refreshToken)
            expired = true
            bootstrap()
            assertEquals("r2", store.saved?.refreshToken)
            close()
        }
        val legacy = SavedRemoteSession(" https://watch.example/ ", requireNotNull(store.saved).refreshToken)
        RemoteConnection(legacy.origin, store, client()).apply {
            restore(legacy)
            assertEquals("r3", store.saved?.refreshToken)
            assertEquals("https://watch.example", store.saved?.origin)
            signOut()
            assertNull(store.saved)
            close()
        }
        assertEquals(3, refreshes)
        assertEquals(true, logout)
    }

    @Test
    fun browserSessionUsesStatusCsrfBeforeRefreshingExpiredCookie() = runBlocking {
        var statusCalls = 0
        var bootstrapCalls = 0
        var refreshed = false
        val client = HttpClient(MockEngine { request ->
            val (body, code) = when (request.url.encodedPath) {
                "/api/auth/status" -> {
                    statusCalls++
                    """{"authenticated":true,"csrf_token":"c$statusCalls"}""" to HttpStatusCode.OK
                }
                "/api/v3/bootstrap" -> {
                    bootstrapCalls++
                    if (!refreshed) "{}" to HttpStatusCode.Unauthorized
                    else """{"identity":{"profiles":[],"profile_id":null},"home":null}""" to HttpStatusCode.OK
                }
                "/api/auth/refresh" -> {
                    assertEquals("c2", request.headers["x-csrf-token"])
                    refreshed = true
                    """{"csrf_token":"c3"}""" to HttpStatusCode.OK
                }
                else -> error("Unexpected request ${request.url.encodedPath}")
            }
            respond(body, code, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        RemoteConnection("https://watch.example", MemoryStore(), client, browserMode = true).apply {
            assertEquals(0, restoreBrowser()?.profiles?.size)
            close()
        }
        assertEquals(2, statusCalls)
        assertEquals(2, bootstrapCalls)
    }

    @Test
    fun nativeLoginStillWorksWhenSecureStorageIsUnavailable() = runBlocking {
        val unavailable = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = error("vault unavailable")
            override suspend fun save(session: SavedRemoteSession) { error("vault unavailable") }
            override suspend fun clear() { error("vault unavailable") }
        }
        val client = HttpClient(MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/api/auth/device/login" -> """{"access_token":"access","refresh_token":"refresh"}"""
                "/api/v3/bootstrap" -> {
                    assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
                    """{"identity":{"profiles":[],"profile_id":null},"home":null}"""
                }
                else -> error("Unexpected request ${request.url.encodedPath}")
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        RemoteConnection("https://watch.example", unavailable, client).apply {
            assertEquals(0, login("member", "password").profiles.size)
            assertEquals(true, persistenceWarning?.contains("Secure storage") == true)
            close()
        }
        Unit
    }
}
