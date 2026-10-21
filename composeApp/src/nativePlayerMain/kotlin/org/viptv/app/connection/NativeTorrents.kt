package org.viptv.app.connection

import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import uniffi.viptv_core.TorrentRuntimeBridge
import uniffi.viptv_core.normalize

/** One private control exchange with the shared torrent runtime worker (control protocol v2). */
internal interface TorrentRuntimePort {
    fun call(command: JsonObject, timeoutMillis: Long = 3_000): JsonObject
    fun isAlive(): Boolean
    fun terminate()
}

/** What a platform supplies to run the shared torrent runtime. */
internal interface TorrentRuntimeHost {
    /** Monotonic milliseconds that keep counting through device suspension. */
    fun elapsedMillis(): Long
    /** The runtime's private, bounded piece cache. */
    val cacheDirectory: String
    /** A fresh worker; the caller owns and terminates it. */
    fun connect(): TorrentRuntimePort
}

internal expect fun platformTorrentRuntime(): TorrentRuntimeHost?

internal actual fun localTorrents(): LocalTorrents? = platformTorrentRuntime()?.let(NativeTorrents::shared)

internal const val NATIVE_TORRENT_POLICY = "public_discovery_verified_v2"

/**
 * Native transport v2 on top of the core grant holder ([TorrentRuntimeBridge]).
 * Core validates every lease envelope and owns the authority deadline; this
 * adapter only moves the private grant into the runtime worker and renews it.
 */
internal class NativeTorrents(private val host: TorrentRuntimeHost) : LocalTorrents {
    override val capability = buildJsonObject { put("version", 2); put("network_policy", NATIVE_TORRENT_POLICY) }
    private val workerLock = Mutex()
    private var worker: TorrentRuntimePort? = null
    private var generation = 0L

    override suspend fun negotiate(remote: RemoteConnection): Boolean {
        val (status, body) = try {
            withTimeout(5_000) { remote.exchange("/api/torrent-runtime-protocol", HttpMethod.Get) }
        } catch (error: TimeoutCancellationException) { return false }
        catch (error: CancellationException) { throw error }
        catch (error: RemoteFailure) { if (error.status in listOf(401, 403)) throw error else return false }
        catch (_: Exception) { return false }
        // Authentication refusal follows ordinary recovery, never a legacy start.
        if (status == 401) throw RemoteFailure(401, "Sign in to continue.")
        if (status == 403) throw RemoteFailure(403, "This action is not available for this profile.")
        if (status != 200 || body.length > 4096) return false
        return try {
            Json.parseToJsonElement(normalize("torrentRuntimeProtocol", body, "")).jsonObject["nativeTorrentVersions"]
                ?.jsonArray?.any { it.jsonPrimitive.intOrNull == 2 } == true
        } catch (_: Exception) { false }
    }

    override suspend fun acquire(remote: RemoteConnection, id: String, request: JsonObject, stage: (String) -> Unit): LocalTorrentPlayback {
        val playback = Playback(remote, id, request, nextGeneration())
        try {
            playback.adoptReady()
            playback.prepare()
            playback.awaitMedia(stage)
            return playback
        } catch (error: Throwable) {
            withContext(NonCancellable) { playback.close() }
            throw error
        }
    }

    @Synchronized private fun nextGeneration() = ++generation

    /** One warm worker serves every playback; a dead one is replaced before the next command. */
    private suspend fun <T> runtime(effect: (TorrentRuntimePort) -> T): T = workerLock.withLock {
        withContext(Dispatchers.IO) {
            val current = worker?.takeIf { it.isAlive() } ?: run {
                worker?.terminate()
                worker = null
                val fresh = try { host.connect() } catch (_: Exception) { throw unavailable() }
                val opened = try {
                    fresh.call(buildJsonObject {
                        put("op", "open"); put("config", buildJsonObject { put("cache_dir", host.cacheDirectory) })
                    }, 8_000)
                } catch (_: Exception) { null }
                if (opened?.ok() != true) { fresh.terminate(); throw unavailable() }
                fresh.also { worker = it }
            }
            // A failed exchange already terminated the worker; callers see the playback failure copy.
            try { effect(current) } catch (error: Exception) { throw error as? RemoteFailure ?: unavailable() }
        }
    }

    private inner class Playback(val remote: RemoteConnection, val id: String, val request: JsonObject, val generation: Long) : LocalTorrentPlayback {
        private val control = Mutex()
        private var bridge: TorrentRuntimeBridge? = null
        private var sequence = 0L
        private var anchorWall = 0L
        private var anchorElapsed = 0L
        private var handle: String? = null
        private var url: String? = null
        private var position = 0.0
        private var renewedAt = 0L
        private var firstFrameSent = false
        @Volatile private var closed = false
        override val mediaUrl get() = url ?: throw unavailable()
        override val positionSeconds get() = position

        /** The grant is adopted from exact envelope bytes, measured against this device's clock. */
        suspend fun adoptReady() = control.withLock {
            val sent = host.elapsedMillis()
            val (status, body) = remote.exchange("/api/playback/${id.encodeURLPathPart()}", HttpMethod.Get)
            val received = host.elapsedMillis()
            if (status != 200) throw unavailable()
            val ready = try { Json.parseToJsonElement(body).jsonObject } catch (_: Exception) { throw unavailable() }
            if (ready["status"]?.jsonPrimitive?.contentOrNull != "ready") throw unavailable()
            // The server applies a saved resume point before granting; the grant must match it.
            position = ready["delivery"]?.jsonObject?.get("position")?.jsonPrimitive?.doubleOrNull ?: throw unavailable()
            bridge = try {
                TorrentRuntimeBridge(buildJsonObject {
                    put("origin", remote.origin.trimEnd('/') + "/"); put("scope", SCOPE); put("generation", generation)
                    put("qualified", true); put("negotiated", true); put("vod", true)
                    put("request", coreRequest(request, position))
                }.toString())
            } catch (_: Exception) { throw unavailable() }
            accept("poll", 200, body, sent, received)
            renewedAt = received
        }

        suspend fun prepare() {
            val source = withGrant { bridge, clock ->
                buildJsonObject {
                    val value = bridge.privateInputValue(clock)
                    when (bridge.privateInputKind(clock)) {
                        "magnet" -> put("magnet", value)
                        "metainfo" -> put("metainfo", value)
                        else -> throw unavailable()
                    }
                    bridge.privateFileIndex(clock)?.let { put("file_index", it.toInt()) }
                    bridge.privateArchiveIndex(clock)?.let { put("archive_index", it.toInt()) }
                    bridge.privateExpectedFileSize(clock)?.let { put("expected_file_size", it.toLong()) }
                    put("trackers", JsonArray(bridge.privateTrackers(clock).map(::JsonPrimitive)))
                }
            }
            val authority = authorityMillis()
            val reply = runtime { it.call(buildJsonObject { put("op", "prepare"); put("source", source); put("authority_ms", authority) }) }
            if (!reply.ok()) throw unavailable()
            handle = reply["handle"]?.jsonPrimitive?.contentOrNull?.takeIf { Regex("[1-9][0-9]{0,18}").matches(it) } ?: throw unavailable()
        }

        /** Observe until the loopback endpoint serves the verified selection; renew while waiting. */
        suspend fun awaitMedia(announce: (String) -> Unit) {
            val started = host.elapsedMillis()
            var announced: String? = null
            while (true) {
                currentCoroutineContext().ensureActive()
                if (host.elapsedMillis() - renewedAt >= RENEW_MILLIS) renew()
                val reply = command("observe")
                val progress = reply["progress"]?.jsonObject ?: throw unavailable()
                val stage = progress["stage"]?.jsonPrimitive?.contentOrNull ?: throw unavailable()
                progress["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { throw failure(stage, it) }
                if (host.elapsedMillis() - started >= STARTUP_MILLIS) throw failure(stage, "startup_deadline")
                // Announce each stage transition once, never byte counters.
                stageCopy(stage)?.takeIf { it != announced }?.let { announced = it; announce(it) }
                val media = reply["media_url"]?.jsonPrimitive?.contentOrNull
                if (progress["ready"]?.jsonPrimitive?.booleanOrNull == true && media != null) {
                    val fileIndex = progress["file_index"]?.jsonPrimitive?.longOrNull ?: throw unavailable()
                    val archive = progress["archive_index"]?.jsonPrimitive?.longOrNull
                    val length = progress["length"]?.jsonPrimitive?.longOrNull ?: throw unavailable()
                    withGrant { bridge, clock -> bridge.bindResolution(fileIndex.toUInt(), archive?.toUInt(), length.toULong(), clock) }
                    val endpoint = try { Url(media) } catch (_: Exception) { throw unavailable() }
                    if (endpoint.protocol != URLProtocol.HTTP || endpoint.host != "127.0.0.1") throw unavailable()
                    url = media
                    return
                }
                delay(200)
            }
        }

        override suspend fun renew() {
            control.withLock {
                if (closed) throw unavailable()
                val sent = host.elapsedMillis()
                val (status, body) = remote.exchange("/api/playback/${id.encodeURLPathPart()}/heartbeat", HttpMethod.Post)
                val received = host.elapsedMillis()
                if (status != 200) throw RemoteFailure(status, "The server playback session ended. Choose the source again.")
                accept("heartbeat", status, body, sent, received)
                renewedAt = received
            }
            command("renew", authorityMillis())
        }

        override suspend fun firstFrame() {
            if (firstFrameSent || closed) return
            command("first_frame")
            firstFrameSent = true
        }

        override suspend fun close() {
            if (closed) return
            closed = true
            handle?.let { current ->
                try { runtime { it.call(buildJsonObject { put("op", "close"); put("handle", current) }, 1_750) } }
                catch (_: Exception) { /* Worker death already revoked the endpoint. */ }
            }
            handle = null
            bridge?.let { holder -> runCatching { holder.invalidate() }; holder.close() }
            bridge = null
        }

        private fun accept(operation: String, status: Int, body: String, sent: Long, received: Long) {
            val holder = bridge ?: throw unavailable()
            val observation = buildJsonObject {
                put("scope", SCOPE); put("generation", generation); put("sequence", ++sequence)
                put("operation", operation); put("receivedAtMillis", received)
                put("roundTripMillis", (received - sent).coerceAtLeast(0))
                put("uncertaintyMillis", 0); put("maxUncertaintyMillis", 0)
                put("trustedWallUpperUnixMillis", JsonNull); put("suspendAware", true)
            }.toString()
            val state = try {
                Json.parseToJsonElement(holder.acceptMeasuredBytes(status.toUShort(), body.encodeToByteArray(), observation)).jsonObject
            } catch (_: Exception) { throw unavailable() }
            if (state["status"]?.jsonPrimitive?.contentOrNull != "ready") throw unavailable()
            anchorWall = holder.trustedWallUpperUnixMillis()?.toLong() ?: throw unavailable()
            anchorElapsed = received
        }

        private fun clock(): String {
            val now = host.elapsedMillis()
            return buildJsonObject {
                put("scope", SCOPE); put("generation", generation); put("nowMillis", now)
                put("trustedWallUpperUnixMillis", anchorWall + (now - anchorElapsed).coerceAtLeast(0))
                put("backendRevalidated", true); put("suspendAware", true)
            }.toString()
        }

        private fun <T> withGrant(read: (TorrentRuntimeBridge, String) -> T): T {
            val holder = bridge ?: throw unavailable()
            return try { read(holder, clock()) } catch (_: Exception) { throw expired() }
        }

        /** Remaining core-computed authority, at most the runtime's sixty seconds. */
        private fun authorityMillis(): Long {
            val state = withGrant { bridge, clock -> Json.parseToJsonElement(bridge.authorize(clock)).jsonObject }
            val deadline = state["deadlineMillis"]?.jsonPrimitive?.longOrNull ?: throw expired()
            return (deadline - host.elapsedMillis()).takeIf { it > 0 }?.coerceAtMost(60_000) ?: throw expired()
        }

        private suspend fun command(op: String, authority: Long? = null): JsonObject {
            val current = handle ?: throw unavailable()
            val reply = runtime { it.call(buildJsonObject {
                put("op", op); put("handle", current); authority?.let { value -> put("authority_ms", value) }
            }) }
            if (!reply.ok()) throw unavailable()
            return reply
        }
    }

    companion object {
        private const val SCOPE = "local"
        private const val RENEW_MILLIS = 20_000L
        private const val STARTUP_MILLIS = 120_000L
        private var instance: Pair<TorrentRuntimeHost, NativeTorrents>? = null
        @Synchronized fun shared(host: TorrentRuntimeHost): NativeTorrents =
            instance?.takeIf { it.first === host }?.second ?: NativeTorrents(host).also { instance = host to it }

        /** The sent start body in core's request shape, bound to the position the server granted. */
        internal fun coreRequest(request: JsonObject, position: Double): JsonObject {
            fun camel(name: String) = name.split('_').mapIndexed { index, part -> if (index == 0) part else part.replaceFirstChar(Char::uppercase) }.joinToString("")
            fun convert(value: JsonElement): JsonElement = when (value) {
                is JsonObject -> JsonObject(value.entries.associate { (key, item) -> camel(key) to convert(item) })
                is JsonArray -> JsonArray(value.map(::convert))
                else -> value
            }
            // `resume` and the audio-index fact are delivery negotiation, not torrent policy (as on the server).
            val client = JsonObject(request.getValue("client").jsonObject - "supports_required_audio_index")
            return convert(JsonObject(request - "resume" + ("client" to client) + ("position" to JsonPrimitive(position)))).jsonObject
        }

        private fun stageCopy(stage: String): String? = try {
            Json.parseToJsonElement(normalize("torrentRuntime", buildJsonObject {
                put("operation", "stage"); put("stage", stage)
            }.toString(), "")).jsonPrimitive.content
        } catch (_: Exception) { null }

        /** Core owns the copy for each observed runtime failure. */
        private fun failure(stage: String, reason: String) = RemoteFailure(502, try {
            Json.parseToJsonElement(normalize("torrentRuntime", buildJsonObject {
                put("operation", "failure"); put("stage", stage); put("reason", reason)
            }.toString(), "")).jsonObject.getValue("message").jsonPrimitive.content
        } catch (_: Exception) { UNAVAILABLE })

        private fun expired() = RemoteFailure(502, try {
            Json.parseToJsonElement(normalize("torrentRuntime", buildJsonObject {
                put("operation", "failure"); put("stage", "buffering"); put("reason", "authority_expired")
            }.toString(), "")).jsonObject.getValue("message").jsonPrimitive.content
        } catch (_: Exception) { UNAVAILABLE })

        private const val UNAVAILABLE = "This torrent could not be played on this device."
        private fun unavailable() = RemoteFailure(502, UNAVAILABLE)
        private fun JsonObject.ok() = this["version"]?.jsonPrimitive?.intOrNull == 2 && this["ok"]?.jsonPrimitive?.booleanOrNull == true
    }
}
