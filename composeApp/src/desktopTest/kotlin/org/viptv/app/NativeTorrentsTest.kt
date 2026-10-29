package org.viptv.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*
import org.viptv.app.connection.*

class NativeTorrentsTest {
    private class Store : RemoteSessionStore {
        override suspend fun read(): SavedRemoteSession? = null
        override suspend fun save(session: SavedRemoteSession) { }
        override suspend fun clear() { }
    }

    private class Worker(val observe: MutableList<JsonObject>) : TorrentRuntimePort {
        val commands = mutableListOf<JsonObject>()
        var alive = true
        override fun call(command: JsonObject, timeoutMillis: Long): JsonObject {
            commands += command
            val ok = buildJsonObject { put("version", 2); put("ok", true) }
            return when (command.getValue("op").jsonPrimitive.content) {
                "prepare" -> JsonObject(ok + ("handle" to JsonPrimitive("1")))
                "observe" -> observe.removeAt(0)
                else -> ok
            }
        }
        override fun isAlive() = alive
        override fun terminate() { alive = false }
    }

    private class Host(val worker: Worker) : TorrentRuntimeHost {
        override fun elapsedMillis() = System.nanoTime() / 1_000_000
        override val cacheDirectory = "/private/torrent-cache"
        override fun connect(): TorrentRuntimePort = worker
    }

    private val hash = "dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c"
    private fun envelope(expiresIn: Long = 60): String {
        val now = System.currentTimeMillis() / 1000
        return """{"id":"lease","status":"ready","expires_at":${now + expiresIn},"renew_after_seconds":20,"error_code":null,"error":null,
            "delivery":{"kind":"native_torrent","live":false,"format":"original","position":42.0,
            "preferences":{"audio_language":"en","subtitle_language":null,"subtitles_enabled":false},
            "grant":{"version":2,"network_policy":"public_discovery_verified_v2","id":"grant_fixture","server_time":$now,"expires_at":${now + expiresIn},
            "info_hash":"$hash","file_index":null,"archive_index":null,"trackers":["udp://tracker.opentrackr.org:1337/announce"],
            "input":{"kind":"magnet","uri":"magnet:?xt=urn:btih:$hash"}}}}"""
    }
    private fun progress(stage: String, ready: Boolean, error: String? = null) = buildJsonObject {
        put("version", 2); put("ok", true)
        if (ready) put("media_url", "http://127.0.0.1:40123/media/opaque-token")
        put("progress", buildJsonObject {
            put("stage", stage); put("ready", ready); put("file_index", 0); put("length", if (ready) 276_134_947 else 0)
            error?.let { put("error", it) }
        })
    }
    private val request = buildJsonObject {
        put("request_id", "0b7f6c38-6d1c-4a5e-9a51-3f3c1b2a9d10"); put("stream_id", "opaque-torrent")
        put("client", buildJsonObject {
            put("platform", "android"); put("can_play_direct", true); put("supports_required_audio_index", true)
            put("max_width", 1920); put("max_height", 1080)
            put("video_codecs", JsonArray(listOf(JsonPrimitive("h264")))); put("audio_codecs", JsonArray(listOf(JsonPrimitive("aac"))))
            put("native_torrent", buildJsonObject { put("version", 2); put("network_policy", "public_discovery_verified_v2") })
        })
        put("force_gateway", false); put("position", 0.0); put("resume", true)
    }

    private fun connection(handler: MockRequestHandler) =
        RemoteConnection("https://watch.example", Store(), HttpClient(MockEngine(handler)) { configureTransport() }, browserMode = true)
    private fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK): MockRequestHandleScope.() -> HttpResponseData =
        { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }

    @Test fun negotiationAcceptsOnlyTheV2RuntimeProtocol() = runBlocking {
        val cases = listOf(
            HttpStatusCode.OK to """{"version":2,"native_torrent_versions":[2]}""" to true,
            HttpStatusCode.OK to """{"version":2,"native_torrent_versions":[]}""" to false,
            HttpStatusCode.NotFound to """{"error":"API route not found"}""" to false,
            HttpStatusCode.OK to """{"version":1,"native_torrent_versions":[1]}""" to false,
        )
        for ((reply, expected) in cases) {
            val remote = connection { request ->
                assertEquals("/api/torrent-runtime-protocol", request.url.encodedPath)
                json(reply.second, reply.first)(this)
            }
            assertEquals(expected, NativeTorrents(Host(Worker(mutableListOf()))).negotiate(remote), reply.second)
            remote.close()
        }
    }

    @Test fun grantIsAcquiredRenewedAndReleasedLocally() = runBlocking {
        val calls = mutableListOf<String>()
        val remote = connection { request ->
            calls += "${request.method.value} ${request.url.encodedPath}"
            json(envelope())(this)
        }
        val worker = Worker(mutableListOf(progress("finding_peers", false), progress("buffering", true)))
        val stages = mutableListOf<String>()
        val playback = NativeTorrents(Host(worker)).acquire(remote, "lease", request) { stages += it }
        assertEquals(listOf("Finding peers…", "Buffering…"), stages)
        assertEquals("http://127.0.0.1:40123/media/opaque-token", playback.mediaUrl)
        assertEquals(42.0, playback.positionSeconds)
        assertEquals(listOf("open", "prepare", "observe", "observe"), worker.commands.map { it.getValue("op").jsonPrimitive.content })
        assertEquals("/private/torrent-cache", worker.commands[0].getValue("config").jsonObject.getValue("cache_dir").jsonPrimitive.content)
        val source = worker.commands[1].getValue("source").jsonObject
        assertEquals("magnet:?xt=urn:btih:$hash", source.getValue("magnet").jsonPrimitive.content)
        assertEquals(listOf("udp://tracker.opentrackr.org:1337/announce"), source.getValue("trackers").jsonArray.map { it.jsonPrimitive.content })
        assertNull(source["file_index"])
        assertTrue(worker.commands[1].getValue("authority_ms").jsonPrimitive.long in 1..60_000)

        playback.renew(); playback.firstFrame(); playback.close()
        assertEquals(listOf("renew", "first_frame", "close"), worker.commands.drop(4).map { it.getValue("op").jsonPrimitive.content })
        assertTrue(worker.commands[4].getValue("authority_ms").jsonPrimitive.long in 1..60_000)
        assertEquals(listOf("GET /api/playback/lease", "POST /api/playback/lease/heartbeat"), calls)
        remote.close()
    }

    @Test fun runtimeFailureUsesCoreCopyAndClosesTheHandle() = runBlocking {
        val remote = connection { json(envelope())(this) }
        val worker = Worker(mutableListOf(progress("finding_peers", false, error = "startup_stalled")))
        val error = assertFailsWith<RemoteFailure> { NativeTorrents(Host(worker)).acquire(remote, "lease", request) }
        assertTrue(error.message!!.isNotBlank())
        assertNotEquals("This torrent could not be played on this device.", error.message)
        assertEquals("close", worker.commands.last().getValue("op").jsonPrimitive.content)
        remote.close()
    }

    @Test fun grantForADifferentPositionOrPolicyIsRefusedBeforeTransport() = runBlocking {
        val remote = connection { json(envelope().replace("public_discovery_verified_v2", "public_dht_tcp_v1"))(this) }
        val worker = Worker(mutableListOf())
        assertFailsWith<RemoteFailure> { NativeTorrents(Host(worker)).acquire(remote, "lease", request) }
        assertTrue(worker.commands.none { it.getValue("op").jsonPrimitive.content == "prepare" })
        remote.close()
    }

    @Test fun coreRequestDropsDeliveryOnlyFactsAndBindsTheGrantedPosition() {
        val core = NativeTorrents.coreRequest(request, 42.0)
        assertNull(core["resume"])
        assertEquals(42.0, core.getValue("position").jsonPrimitive.double)
        val client = core.getValue("client").jsonObject
        assertNull(client["supportsRequiredAudioIndex"])
        assertEquals("public_discovery_verified_v2", client.getValue("nativeTorrent").jsonObject.getValue("networkPolicy").jsonPrimitive.content)
        assertEquals("opaque-torrent", core.getValue("streamId").jsonPrimitive.content)
    }
}
