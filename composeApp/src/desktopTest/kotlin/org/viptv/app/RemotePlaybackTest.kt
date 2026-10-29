package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.test.*
import org.viptv.app.connection.*
import org.viptv.engine.wire.SourceCandidate

class RemotePlaybackTest {
    private class Store : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) { }
        override suspend fun clear() { }
    }
    private val facts = buildJsonObject {
        put("platform", "web"); put("can_play_direct", false)
        put("max_width", 1920); put("max_height", 1080)
        put("video_codecs", JsonArray(listOf(JsonPrimitive("h264")))); put("audio_codecs", JsonArray(listOf(JsonPrimitive("aac"))))
    }

    @Test fun gatewayLeaseKeepsControlAuthenticatedAndStops() = runBlocking {
        val calls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            if (!request.url.encodedPath.startsWith("/api/auth/")) assertEquals("fixture-csrf", request.headers["x-csrf-token"])
            if (request.url.encodedPath.startsWith("/api/")) calls += "${request.method.value} ${request.url.encodedPath}"
            val path = request.url.encodedPath
            if (path.endsWith("/heartbeat")) assertEquals(0L, request.body.contentLength ?: 0L)
            if (path.endsWith("/first-frame")) assertEquals("{}", (request.body as TextContent).text)
            val response = when (path) {
                "/api/auth/login" -> """{"csrf_token":"fixture-csrf"}"""
                "/api/bootstrap" -> """{"identity":{"profiles":[],"profile_id":null}}"""
                "/api/playback" -> {
                    val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    assertTrue(body.getValue("force_gateway").jsonPrimitive.boolean)
                    assertTrue(body.getValue("resume").jsonPrimitive.boolean)
                    assertEquals("opaque-stream", body.getValue("stream_id").jsonPrimitive.content)
                    assertFalse(body.getValue("client").jsonObject.getValue("can_play_direct").jsonPrimitive.boolean)
                    """{"id":"lease","status":"starting"}"""
                }
                "/api/playback/lease" -> if (request.method == HttpMethod.Delete) "{}" else
                    """{"id":"lease","status":"ready","renew_after_seconds":20,"delivery":{"kind":"gateway","url":"https://media.example/scoped/index.m3u8","position":0,"timeline_offset_seconds":3,"duration":60,"required_audio_index":9}}"""
                else -> "{}"
            }
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        connection.login("fixture", "disposable")
        val lease = connection.openPlayback(SourceCandidate("opaque-stream", "addon:1", "Fixture"), "profile", facts)
        assertEquals("https://media.example/scoped/index.m3u8", lease.input.uri)
        assertEquals(0L, lease.input.positionMillis)
        assertEquals(3000L, lease.input.timelineOffsetMillis)
        assertEquals(60_000L, lease.input.durationMillis)
        assertEquals("profile", lease.input.profileId)
        assertNull(lease.input.requiredAudioIndex)
        lease.firstFrame(); lease.renew(); lease.close()
        assertEquals(listOf("POST /api/auth/login", "GET /api/bootstrap", "POST /api/playback", "GET /api/playback/lease", "POST /api/playback/lease/first-frame",
            "POST /api/playback/lease/heartbeat", "DELETE /api/playback/lease"), calls)
        connection.close()
    }

    @Test fun directPlaybackRenewsWithoutGatewayFrameAcknowledgment() = runBlocking {
        val calls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            calls += request.url.encodedPath
            if (request.url.encodedPath.endsWith("/heartbeat")) assertEquals(0L, request.body.contentLength ?: 0L)
            respond(if (request.url.encodedPath == "/api/playback")
                """{"id":"direct","status":"ready","delivery":{"kind":"direct","url":"https://media.example/movie.mp4","position":0,"required_audio_index":1}}"""
                else "{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        val native = buildJsonObject { put("can_play_direct", true) }
        val lease = connection.openPlayback(SourceCandidate("opaque", "addon:1", "Fixture"), "profile", native)
        assertEquals(1, lease.input.requiredAudioIndex)
        lease.firstFrame(); lease.renew(); lease.close()
        assertFalse(calls.any { it.endsWith("/first-frame") })
        assertTrue(calls.any { it.endsWith("/heartbeat") })
        connection.close()
    }

    @Test fun audioSelectionCapabilityRequiresHostBootstrapAdvertisement() = runBlocking {
        for (supported in listOf(false, true)) {
            var sent: JsonObject? = null
            val client = HttpClient(MockEngine { request ->
                val body = if (request.url.encodedPath == "/api/bootstrap")
                    """{"identity":{"profiles":[],"profile_id":null},"playback_capabilities":{"required_audio_index":$supported}}"""
                else {
                    sent = Json.parseToJsonElement((request.body as TextContent).text).jsonObject.getValue("client").jsonObject
                    """{"id":"direct","status":"ready","delivery":{"kind":"direct","url":"https://media.example/movie.mp4"}}"""
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }) { configureTransport() }
            val connection = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
            try {
                connection.bootstrap()
                connection.openPlayback(SourceCandidate("opaque", "addon:1", "Fixture"), "profile", buildJsonObject {
                    put("can_play_direct", true); put("supports_required_audio_index", true)
                })
                assertEquals(if (supported) JsonPrimitive(true) else null, sent?.get("supports_required_audio_index"))
            } finally { connection.close() }
        }
    }

    private class FakeTorrents(val supported: Boolean, val failure: Exception? = null) : LocalTorrents {
        val events = mutableListOf<String>()
        var acquired: JsonObject? = null
        override val capability = buildJsonObject { put("version", 2); put("network_policy", "public_discovery_verified_v2") }
        override suspend fun negotiate(remote: RemoteConnection): Boolean { events += "negotiate"; return supported }
        override suspend fun acquire(remote: RemoteConnection, id: String, request: JsonObject, stage: (String) -> Unit): LocalTorrentPlayback {
            events += "acquire $id"; acquired = request
            stage("Finding peers…")
            failure?.let { throw it }
            return object : LocalTorrentPlayback {
                override val mediaUrl = "http://127.0.0.1:41999/media/private"
                override val positionSeconds = 12.5
                override suspend fun renew() { events += "renew" }
                override suspend fun firstFrame() { events += "first_frame" }
                override suspend fun close() { events += "close" }
            }
        }
    }
    private val androidFacts = buildJsonObject {
        put("platform", "android"); put("can_play_direct", true); put("supports_required_audio_index", true)
        put("max_width", 1920); put("max_height", 1080)
        put("video_codecs", JsonArray(listOf(JsonPrimitive("h264")))); put("audio_codecs", JsonArray(listOf(JsonPrimitive("aac"))))
    }
    private val nativeReady = """{"id":"lease","status":"ready","expires_at":1792562096,"renew_after_seconds":20,"error_code":null,"error":null,
        "delivery":{"kind":"native_torrent","live":false,"format":"original","position":12.5,"preferences":{"audio_language":null,"subtitle_language":null,"subtitles_enabled":false},"grant":{}}}"""

    @Test fun torrentGrantPlaysTheDevicesOwnRuntime() = runBlocking {
        val calls = mutableListOf<String>()
        var sent: JsonObject? = null
        val client = HttpClient(MockEngine { request ->
            calls += "${request.method.value} ${request.url.encodedPath}"
            val response = when (request.url.encodedPath) {
                "/api/playback" -> { sent = Json.parseToJsonElement((request.body as TextContent).text).jsonObject; """{"id":"lease","status":"starting"}""" }
                "/api/playback/lease" -> if (request.method == HttpMethod.Delete) "{}" else nativeReady
                else -> "{}"
            }
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        val torrents = FakeTorrents(supported = true)
        val stages = mutableListOf<String>()
        val lease = connection.openPlayback(SourceCandidate("opaque-torrent", "addon:1", "Fixture"), "profile", androidFacts, torrents = torrents, stage = { stages += it })
        assertEquals(listOf("Finding peers…"), stages)
        val client0 = sent!!.getValue("client").jsonObject
        assertEquals(torrents.capability, client0["native_torrent"])
        assertFalse(sent!!.getValue("force_gateway").jsonPrimitive.boolean)
        assertEquals(sent, torrents.acquired)
        assertEquals("http://127.0.0.1:41999/media/private", lease.input.uri)
        assertEquals(12_500L, lease.input.positionMillis)
        assertTrue(lease.input.localTorrent)
        assertFalse(lease.reopensForSeek)
        lease.firstFrame(); lease.renew(); lease.close()
        assertEquals(listOf("negotiate", "acquire lease", "first_frame", "renew", "close"), torrents.events)
        assertFalse(calls.any { it.endsWith("/first-frame") || it.endsWith("/heartbeat") })
        assertEquals("DELETE /api/playback/lease", calls.last())
        connection.close()
    }

    @Test fun serverWithoutNativeTorrentsKeepsTheLegacyStart() = runBlocking {
        var sent: JsonObject? = null
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/api/playback") sent = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond("""{"id":"direct","status":"ready","delivery":{"kind":"direct","url":"https://media.example/movie.mp4"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        val torrents = FakeTorrents(supported = false)
        val lease = connection.openPlayback(SourceCandidate("opaque", "addon:1", "Fixture"), "profile", androidFacts, torrents = torrents)
        assertNull(sent!!.getValue("client").jsonObject["native_torrent"])
        assertEquals("https://media.example/movie.mp4", lease.input.uri)
        assertEquals(listOf("negotiate"), torrents.events)
        connection.close()
    }

    @Test fun failedLocalAcquisitionReleasesTheLease() = runBlocking {
        val calls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            calls += "${request.method.value} ${request.url.encodedPath}"
            respond(if (request.url.encodedPath == "/api/playback") """{"id":"lease","status":"starting"}"""
                else if (request.method == HttpMethod.Delete) "{}" else nativeReady,
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        val torrents = FakeTorrents(supported = true, failure = RemoteFailure(502, "No peers are sharing this torrent right now."))
        val error = assertFailsWith<RemoteFailure> {
            connection.openPlayback(SourceCandidate("opaque", "addon:1", "Fixture"), "profile", androidFacts, torrents = torrents)
        }
        assertEquals("No peers are sharing this torrent right now.", error.message)
        assertEquals("DELETE /api/playback/lease", calls.last())
        connection.close()
    }

    @Test fun gatewayDeliveryOnDirectDeviceIsAPlaybackFailure() = runBlocking {
        val client = HttpClient(MockEngine { request ->
            respond(if (request.method == HttpMethod.Delete) "{}" else
                """{"id":"lease","status":"ready","delivery":{"kind":"gateway","url":"https://media.example/index.m3u8"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        assertFailsWith<RemoteFailure> {
            connection.openPlayback(SourceCandidate("opaque", "addon:1", "Fixture"), "profile", androidFacts, torrents = null)
        }
        connection.close()
    }

    @Test fun cancellingStartingLeaseStopsIt() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val stopped = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Delete) stopped += request.url.encodedPath
            else if (request.method == HttpMethod.Get) { started.complete(Unit); awaitCancellation() }
            respond(if (request.method == HttpMethod.Delete) "{}" else """{"id":"pending","status":"starting"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        val task = launch { connection.openPlayback(SourceCandidate("opaque", "addon:1", "Fixture"), "profile", facts) }
        started.await(); yield(); task.cancelAndJoin()
        assertEquals(listOf("/api/playback/pending"), stopped)
        connection.close()
    }

    @Test fun subtitleContentRefreshesDeviceSessionAndRemainsRawVtt() = runBlocking {
        var subtitleRequests = 0
        var refreshes = 0
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val body = when (path) {
                "/api/auth/device/refresh" -> { refreshes++; """{"access_token":"fresh$refreshes","refresh_token":"refresh-next"}""" }
                "/api/bootstrap" -> """{"identity":{"profiles":[],"profile_id":null}}"""
                else -> {
                    subtitleRequests++
                    assertEquals("/api/subtitle-content/opaque", path)
                    assertEquals("Bearer fresh$subtitleRequests", request.headers[HttpHeaders.Authorization])
                    if (subtitleRequests == 1) return@MockEngine respond("{}", HttpStatusCode.Unauthorized)
                    "WEBVTT\n\n00:00.000 --> 00:01.000\nFixture cue\n"
                }
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, if (path.contains("subtitle-content")) "text/vtt" else "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", Store(), client, browserMode = false)
        connection.restore(SavedRemoteSession("https://watch.example", "saved-refresh"))
        assertTrue(connection.subtitleContent("opaque").startsWith("WEBVTT\n"))
        assertEquals(2, subtitleRequests)
        assertEquals(2, refreshes)
        connection.close()
    }

    @Test fun cancellingBeforeStartReplyCancelsByRequestAuthority() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                started.complete(Unit)
                awaitCancellation()
            }
            cancelled += request.url.encodedPath
            respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureTransport() }
        val connection = RemoteConnection("https://watch.example", Store(), client, browserMode = true)
        val task = launch { connection.openPlayback(SourceCandidate("opaque", "addon:1", "Fixture"), "profile", facts) }
        started.await(); task.cancelAndJoin()
        assertEquals(1, cancelled.size)
        assertTrue(cancelled.single().startsWith("/api/playback-requests/"))
        connection.close()
    }
}
