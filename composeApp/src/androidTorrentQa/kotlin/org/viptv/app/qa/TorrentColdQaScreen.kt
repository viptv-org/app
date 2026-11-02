package org.viptv.app.qa

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.player.*
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Compiled only with -Pviptv.torrentQa=true; no normal application entry point. */
@Composable
fun TorrentColdQaScreen(raw: String, files: File, finish: () -> Unit) {
    val spec = remember { Json.parseToJsonElement(raw).jsonObject }
    var input by remember { mutableStateOf<PlaybackInput?>(null) }
    val first = remember { CompletableDeferred<Long>() }
    val commands = remember { MutableSharedFlow<PlayerCommand>() }
    val samples = remember { mutableListOf<String>() }
    var position by remember { mutableLongStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    input?.let { media -> PlayerSurface(media, false, Modifier.fillMaxSize().background(Color.Black), observe = { fact ->
        when (fact) {
            is PlayerFact.Presentation -> if (fact.proof == PresentationProof.NativeRendererPresented)
                first.complete(requireNotNull(fact.atNanos))
            is PlayerFact.Progress -> position = fact.positionMillis
            PlayerFact.Failed, is PlayerFact.ControlFailed -> { failed = true; first.completeExceptionally(IllegalStateException("player failed")) }
            else -> Unit
        }
    }, commands = commands) }
    LaunchedEffect(Unit) {
        val fields = mutableMapOf<String, JsonElement>("passed" to JsonPrimitive(false))
        val mode = spec["mode"]?.jsonPrimitive?.content ?: "cold"
        val trial = spec.getValue("trial_id").jsonPrimitive.content
        require(Regex("[a-zA-Z0-9_-]{1,100}").matches(trial))
        val root = files.resolve("torrent-qa-cache-$trial")
        val timeout = spec["timeout_ms"]?.jsonPrimitive?.longOrNull ?: 60_000
        val reference = spec["reference"]?.jsonPrimitive?.booleanOrNull == true
        var worker: TorrentRuntimePort? = null
        var handle: String? = null
        var mediaUrl: String? = null
        var retirement = "not_started"
        var stage = "start"
        var admissionStarted = 0L
        var deadlineNanos = 0L
        val controlErrors = mutableListOf<JsonElement>()
        fun remainingMillis() = ((deadlineNanos-SystemClock.elapsedRealtimeNanos())/1_000_000).coerceAtLeast(1)
        fields["mode"] = JsonPrimitive(mode)
        fields["trial_id"] = JsonPrimitive(trial)
        fields["engine"] = JsonPrimitive(if (reference) "reference" else "native")
        suspend fun call(op: String, body: JsonObject = buildJsonObject {}): JsonObject = withContext(Dispatchers.IO) {
            requireNotNull(worker).call(buildJsonObject {
                put("op", op); handle?.let { put("handle", it) }; body.forEach { (k,v) -> put(k,v) }
            }, 8_000).also { reply ->
                reply["error"]?.let { error -> controlErrors.add(buildJsonObject { put("op", op); put("error", error) }) }
            }
        }
        suspend fun connect(cache: File) {
            handle = null
            stage = "host_connect"
            worker = withContext(Dispatchers.IO) { requireNotNull(platformTorrentRuntime()).connect() }
            stage = "runtime_open"
            check(call("open", buildJsonObject { put("config", buildJsonObject {
                put("cache_dir", cache.absolutePath); put("cache_bytes", 2L shl 30)
            }) })["ok"]?.jsonPrimitive?.booleanOrNull == true)
        }
        suspend fun retire() {
            val owned = worker ?: return
            var forced = false
            suspend fun stop(op: String): Boolean {
                val reply = call(op)
                if (reply["terminate"]?.jsonPrimitive?.booleanOrNull == true) {
                    check(reply["ok"]?.jsonPrimitive?.booleanOrNull == false && reply["error"]?.jsonPrimitive?.content == "settlement_timeout")
                    return true
                }
                check(reply["ok"]?.jsonPrimitive?.booleanOrNull == true)
                return false
            }
            try {
                forced = handle != null && stop("close")
                if (!forced) forced = stop("shutdown")
                retirement = if (forced) "forced" else "joined"
            } finally {
                withContext(Dispatchers.IO) { owned.terminate() }
                check((owned as TorrentRuntimeWorker).actualWorkerAlive().not())
                fields["worker_binder_dead"] = JsonPrimitive(true)
                worker = null; handle = null
            }
        }
        suspend fun denied(url: String): Boolean = withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"; connection.connectTimeout = 1500; connection.readTimeout = 1500
                try { connection.responseCode in listOf(401,403,404,410) } finally { connection.disconnect() }
            } catch (_: java.net.ConnectException) { true }
              catch (_: java.io.IOException) { false }
        }
        try {
            check(!root.exists()) { "cold cache already exists" }
            val fixture = spec["local_fixture"]?.jsonObject
            if (fixture != null) {
                check(mode != "cold") { "fixture cache cannot count as cold" }
                withContext(Dispatchers.IO) { seedVerifiedFixture(files, root, fixture) }
                fields["functional_fixture_only"] = JsonPrimitive(true)
            }
            fields["cache_empty_at_start"] = JsonPrimitive(fixture == null)
            val start = SystemClock.elapsedRealtimeNanos()
            admissionStarted = start
            deadlineNanos = start + timeout*1_000_000L
            val url = if (reference) {
                files.resolve("torrent-qa-reference-ready").writeText("ready")
                val reply = files.resolve("torrent-qa-reference-reply.json")
                withTimeout(remainingMillis()) { while (!reply.isFile) delay(20) }
                Json.parseToJsonElement(reply.readText()).jsonObject.getValue("url").jsonPrimitive.content.also {
                    val parsed = URI(it); require(parsed.scheme == "http" && parsed.host in listOf("127.0.0.1", "::1", "[::1]"))
                }
            } else {
                connect(root)
                stage = "source_prepare"
                val prepared = call("prepare", buildJsonObject {
                    put("source", spec.getValue("source")); put("authority_ms", if (mode == "expire") 1500 else 60000)
                })
                check(prepared["ok"]?.jsonPrimitive?.booleanOrNull == true)
                handle = prepared.getValue("handle").jsonPrimitive.content
                if (mode == "cancel") {
                    check(call("observe")["media_url"] == null)
                    val at = SystemClock.elapsedRealtimeNanos(); retire()
                    fields["cancel_seconds"] = JsonPrimitive((SystemClock.elapsedRealtimeNanos()-at)/1e9)
                    fields["passed"] = JsonPrimitive(true)
                    return@LaunchedEffect
                }
                withTimeout(remainingMillis()) {
                    var ready: String? = null
                    while (ready == null) {
                        val observed = call("observe")
                        check(observed["progress"]?.jsonObject?.get("error") == null)
                        ready = observed["media_url"]?.jsonPrimitive?.content
                        if (ready == null) delay(25)
                    }
                    ready
                }
            }
            mediaUrl = url
            fields["endpoint_seconds"] = JsonPrimitive((SystemClock.elapsedRealtimeNanos()-start)/1e9)
            if (mode == "expire") {
                delay(1800)
                check(denied(url)); fields["expired_endpoint_denied"] = JsonPrimitive(true)
            } else {
                stage = "player_first_frame"
                input = PlaybackInput("torrent-android-qa", url, localTorrent = true, diagnostics = { samples.add(it) })
                val rendered = withTimeout(remainingMillis()) { first.await() }
                check(rendered >= start)
                fields["first_frame_seconds"] = JsonPrimitive((rendered-start)/1e9)
                fields["presentation_proof"] = JsonPrimitive("Media3 onRenderedFirstFrame, elapsedRealtime renderTimeMs; emulator output")
                files.resolve("torrent-qa-frame.json").writeText(JsonObject(fields).toString())
                if (!reference) check(call("first_frame")["ok"]?.jsonPrimitive?.booleanOrNull == true)
                delay(if (mode == "restart_integrity") 8000 else 1500)
                fields["position_ms"] = JsonPrimitive(position)
                check(!failed && position > 0)
                input = null; delay(100)
                if (mode == "kill") {
                    withContext(Dispatchers.IO) { requireNotNull(worker).terminate() }
                    check((worker as TorrentRuntimeWorker).actualWorkerAlive().not()); worker = null; handle = null
                    check(denied(url)); fields["killed_endpoint_denied"] = JsonPrimitive(true)
                    connect(root); fields["reopen_after_kill"] = JsonPrimitive(true)
                } else if (!reference) {
                    retire(); check(denied(url)); fields["closed_endpoint_denied"] = JsonPrimitive(true)
                    if (mode == "restart_integrity") {
                        val valid = withContext(Dispatchers.IO) { verifiedRecords(root) }
                        check(valid.isNotEmpty()); fields["verified_records_before_restart"] = JsonPrimitive(valid.size)
                        connect(root)
                        check(withContext(Dispatchers.IO) { valid.all { verifiedRecord(it) } })
                        fields["verified_records_retained_on_restart"] = JsonPrimitive(true)
                        retire()
                        val damaged = files.resolve("torrent-qa-damaged-$trial")
                        check(!damaged.exists()); root.copyRecursively(damaged)
                        val corrupt = damaged.resolve(valid.first().name)
                        RandomAccessFile(corrupt,"rw").use { it.seek(64); val value=it.readByte(); it.seek(64); it.writeByte(value.toInt() xor 1) }
                        val partial = damaged.resolve("0000000000000000000000000000000000000001-00000000.piece")
                        valid.first().copyTo(partial)
                        RandomAccessFile(partial,"rw").use { it.seek(16); it.writeByte(0) }
                        connect(damaged)
                        check(!corrupt.exists() && !partial.exists())
                        fields["corrupt_and_partial_records_rejected"] = JsonPrimitive(true)
                    }
                }
            }
            fields["passed"] = JsonPrimitive(true)
        } catch (error: Exception) {
            if (admissionStarted != 0L) fields["failed_after_seconds"] = JsonPrimitive((SystemClock.elapsedRealtimeNanos()-admissionStarted)/1e9)
            files.resolve("torrent-qa-error.txt").writeText(error.stackTraceToString())
            fields["error_stage"] = JsonPrimitive(stage)
            fields["error_type"] = JsonPrimitive(error.javaClass.simpleName)
            fields["passed"] = JsonPrimitive(false)
        } finally {
            withContext(NonCancellable) {
                input = null; delay(100)
                if (worker != null) try { retire() } catch (_: Exception) {
                    fields["passed"] = JsonPrimitive(false); fields["retirement_error"] = JsonPrimitive(true)
                    withContext(Dispatchers.IO) { worker?.terminate() }
                }
                fields["worker_retirement"] = JsonPrimitive(retirement)
                fields["control_errors"] = JsonArray(controlErrors)
                fields["player_samples"] = JsonArray(samples.map(::JsonPrimitive))
                files.resolve("torrent-qa-output.json").writeText(JsonObject(fields).toString())
                finish()
            }
        }
    }
}

private fun seedVerifiedFixture(files: File, root: File, fixture: JsonObject) {
    val hash = fixture.getValue("info_hash").jsonPrimitive.content
    require(Regex("[0-9a-f]{40}").matches(hash))
    val pieceLength = fixture.getValue("piece_length").jsonPrimitive.int
    require(pieceLength in 4096..(4 shl 20))
    root.mkdirs()
    files.resolve("torrent-qa-fixture.mp4").inputStream().use { input ->
        var index=0
        while (true) {
            val bytes=input.readNBytes(pieceLength)
            if (bytes.isEmpty()) break
            val header=ByteArray(64)
            "VTRPC001".toByteArray().copyInto(header)
            ByteBuffer.wrap(header,8,8).order(ByteOrder.LITTLE_ENDIAN).putLong(bytes.size.toLong())
            header[16]=1
            MessageDigest.getInstance("SHA-256").digest(bytes).copyInto(header,32)
            root.resolve("$hash-${index.toString(16).padStart(8,'0')}.piece").outputStream().use { out -> out.write(header);out.write(bytes) }
            index++
        }
    }
}

private fun verifiedRecords(root: File): List<File> = root.listFiles().orEmpty().filter {
    it.name.substringAfterLast('-').substringBefore('.').toLongOrNull(16)?.let { index -> index < 0xfffffffdL } == true && verifiedRecord(it)
}
private fun verifiedRecord(file: File): Boolean = try {
    if (!file.name.endsWith(".piece") || !file.isFile || file.length() < 65) false else {
        file.inputStream().use { input ->
            val header = ByteArray(64); check(input.read(header) == 64)
            val length = ByteBuffer.wrap(header,8,8).order(ByteOrder.LITTLE_ENDIAN).long
            if (String(header,0,8) != "VTRPC001" || header[16] != 1.toByte() || file.length() != length+64) false else {
                val digest=MessageDigest.getInstance("SHA-256"); val buffer=ByteArray(65536)
                while (true) { val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n) }
                digest.digest().contentEquals(header.copyOfRange(32,64))
            }
        }
    }
} catch (_: Exception) { false }
