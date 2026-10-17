package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.viptv.app.connection.*
import org.viptv.engine.wire.*
import kotlin.test.*

class RemoteSourcePresentationTest {
    @Test fun serverSourceFactsReachTheSamePresentationAsNativeSources() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            val body = if (request.method == HttpMethod.Post) """{"id":"job"}""" else
                """{"events":[{"source":"addon:1","streams":[{"id":"grant","name":"Release","detail":"Movie.1080p.HEVC.mkv\nProvider notes","quality_height":1080,"presentation":{"provider_name":"Provider","delivery_format":"hls","video_codec":"HEVC","audio_codecs":["EAC3"],"size_bytes":1234}}]}],"done":true}"""
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            val source = remote.sources(Card(MediaIdentity("title", "movie", emptyList()), provider_id = "simkl", title = "Title", badges = emptyList())).single()
            assertEquals("Movie.1080p.HEVC.mkv\nProvider notes", source.detail)
            assertEquals("Provider", source.presentation?.provider_name)
            assertEquals("hls", source.presentation?.delivery_format)
            assertEquals("HEVC", source.presentation?.video_codec)
            assertEquals(listOf("EAC3"), source.presentation?.audio_codecs)
            assertEquals(1234L, source.presentation?.size_bytes)
        } finally { remote.close() }
    }
    @Test fun leavingDiscoveryCancelsTheJobWithoutWaitingForOtherProviders() = runBlocking {
        var deleted = false
        val client = HttpClient(MockEngine { request ->
            val body = when (request.method) {
                HttpMethod.Post -> """{"id":"job"}"""
                HttpMethod.Delete -> { deleted = true; "{}" }
                else -> """{"events":[{"source":"addon:1","streams":[{"id":"grant","name":"Source"}]}],"done":false}"""
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val store = object : RemoteSessionStore {
            override suspend fun read(): SavedRemoteSession? = null
            override suspend fun save(session: SavedRemoteSession) = Unit
            override suspend fun clear() = Unit
        }
        val remote = RemoteConnection("https://watch.example", store, client, browserMode = false)
        try {
            assertFailsWith<kotlinx.coroutines.CancellationException> {
                remote.sources(Card(MediaIdentity("title", "movie", emptyList()), provider_id = "simkl", title = "Title", badges = emptyList())) {
                    throw kotlinx.coroutines.CancellationException("Selected a source")
                }
            }
            assertTrue(deleted)
        } finally { remote.close() }
    }

}
