package org.viptv.app

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.NativeLocalConnection
import org.viptv.app.connection.platformClient
import org.viptv.engine.wire.Card
import org.viptv.engine.wire.MediaIdentity
import uniffi.viptv_core.SecretStore
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.charset.Charset
import kotlin.test.*

class LocalSubtitlesTest {
    private class Secrets : SecretStore {
        private val entries = mutableMapOf<String, String>()
        override fun get(key: String): String? = entries[key]
        override fun put(key: String, value: String) { entries[key] = value }
        override fun remove(key: String) { entries.remove(key) }
    }

    @Test fun realHttpDiscoveryPreservesBytesAndRejectsRevokedScope() = runBlocking {
        val directory = Files.createTempDirectory("viptv-local-subtitles-")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val origin = "http://127.0.0.1:${server.address.port}"
        val bytes = "1\n00:00:00,000 --> 00:00:03,000\nCafé fixture\n".toByteArray(Charset.forName("windows-1252"))
        val requests = mutableListOf<String>()
        server.createContext("/") { request ->
            synchronized(requests) { requests += request.requestURI.toString() }
            val body = when {
                request.requestURI.path == "/manifest.json" -> """{"id":"qa","name":"Subtitle QA","version":"1","resources":["stream","subtitles"],"types":["movie"]}""".toByteArray()
                request.requestURI.path.startsWith("/stream/") -> """{"streams":[{"url":"$origin/movie.mp4","name":"Clip"}]}""".toByteArray()
                request.requestURI.path.startsWith("/subtitles/") -> """{"subtitles":[{"id":"latin","lang":"eng","url":"$origin/caption.srt"}]}""".toByteArray()
                else -> bytes
            }
            request.sendResponseHeaders(200, body.size.toLong())
            request.responseBody.use { it.write(body) }
        }
        server.start()
        val connection = NativeLocalConnection(directory.resolve("qa.sqlite").toString(), Secrets(), platformClient())
        try {
            val addon = connection.installAddon("default", "$origin/manifest.json").single()
            val card = Card(MediaIdentity("tt123", "movie", emptyList()), addon.id, "Fixture", badges = emptyList())
            val source = connection.sources("default", card).single()
            val input = connection.openSource(source.id)
            val subtitles = connection.subtitles("default", card)
            assertEquals(1, subtitles.candidates.size)
            val candidate = subtitles.candidates.single()
            assertFalse(candidate.id.contains("caption.srt"))
            assertTrue(requests.contains("/subtitles/movie/tt123.json"))
            assertContentEquals(bytes, connection.subtitleContent(candidate.id, input))
            connection.setAddonEnabled("default", addon.id, false)
            val before = synchronized(requests) { requests.size }
            assertFails { connection.subtitleContent(candidate.id, input) }
            assertEquals(before, synchronized(requests) { requests.size }, "Revoked choices must fail before egress")
        } finally {
            connection.close(); server.stop(0); directory.toFile().deleteRecursively()
        }
    }
}
