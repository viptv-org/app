package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.RemoteConnection
import org.viptv.app.connection.RemoteSessionStore
import org.viptv.app.connection.SavedRemoteSession
import org.viptv.app.connection.configureTransport
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class GatewayReachabilityTest {
    private val store = object : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) = Unit
        override suspend fun clear() = Unit
    }

    private fun reachability(answer: () -> Pair<HttpStatusCode, String?>): List<Boolean> = runBlocking {
        val seen = mutableListOf<Boolean>()
        val client = HttpClient(MockEngine {
            val (status, type) = answer()
            respond("body", status, type?.let { headersOf(HttpHeaders.ContentType, it) } ?: headersOf())
        }) { configureTransport() }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        remote.onBackendReachability = { seen += it }
        remote.probeHealth(1_000)
        remote.close()
        seen
    }

    @Test fun aTunnelErrorIsAnUnreachableServerAndTheServersOwnErrorIsNot() {
        assertEquals(listOf(false), reachability { HttpStatusCode(530, "Origin unreachable") to "text/html" })
        assertEquals(listOf(false), reachability { HttpStatusCode.BadGateway to "text/html" })
        assertEquals(listOf(false), reachability { HttpStatusCode.GatewayTimeout to null })
        assertEquals(listOf(true), reachability { HttpStatusCode.BadGateway to "application/json" })
        assertEquals(listOf(true), reachability { HttpStatusCode.OK to "application/json" })
        assertEquals(listOf(false), reachability { throw IOException("connection reset") })
    }
}
