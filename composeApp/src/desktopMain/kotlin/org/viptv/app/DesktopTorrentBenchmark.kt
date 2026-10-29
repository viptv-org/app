package org.viptv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.viptv.app.connection.*
import org.viptv.app.player.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.util.concurrent.atomic.AtomicLong

internal object DesktopTorrentFrames {
    @Volatile var sink: ((Long, Long) -> Unit)? = null
    fun presented(positionMillis: Long, atNanos: Long) { sink?.invoke(positionMillis, atNanos) }
}

internal fun verifyPackagedTorrentRuntime() {
    val host = requireNotNull(platformTorrentRuntime())
    val cache = Files.createTempDirectory("viptv-torrent-package-")
    try {
        repeat(2) {
            val worker = host.connect()
            try {
                val opened = worker.call(buildJsonObject { put("op", "open"); put("config", buildJsonObject { put("cache_dir", cache.toString()) }) }, 8_000)
                check(opened["ok"]?.jsonPrimitive?.booleanOrNull == true)
                check(worker.call(buildJsonObject { put("op", "shutdown") })["ok"]?.jsonPrimitive?.booleanOrNull == true)
            } finally { worker.terminate() }
        }
        Files.writeString(Path.of(requireNotNull(System.getenv("VIPTV_TORRENT_SMOKE_TEST"))), "verified-torrent-worker\n")
    } finally { cache.toFile().deleteRecursively() }
}

internal fun launchDesktopTorrentBenchmark() = application {
    val specification = remember {
        Json.parseToJsonElement(Files.readString(Path.of(requireNotNull(System.getenv("VIPTV_TORRENT_BENCH_INPUT"))))).jsonObject
    }
    val result = remember { Path.of(specification.getValue("result").jsonPrimitive.content) }
    val frames = remember { MutableStateFlow(Triple(0L, 0L, 0L)) }
    val firstFrameAt = remember { AtomicLong(0) }
    val observedDuration = remember { MutableStateFlow(0L) }
    val commands = remember { MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 8) }
    var input by remember { mutableStateOf<PlaybackInput?>(null) }
    val samples = remember { mutableListOf<String>() }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        DesktopTorrentFrames.sink = { position, at ->
            firstFrameAt.compareAndSet(0, at)
            frames.update { Triple(it.first + 1, position, at) }
        }
        onDispose { DesktopTorrentFrames.sink = null }
    }
    Window(onCloseRequest = ::exitApplication, title = "Torrent performance measurement",
        state = rememberWindowState(width = 1280.dp, height = 720.dp)) {
        org.viptv.app.player.mpv.MpvWindow(window) {
        input?.let { media ->
            PlayerSurface(media, false, Modifier.fillMaxSize().background(Color.Black), observe = {
                if (it is PlayerFact.Progress) it.durationMillis?.takeIf { duration -> duration > 0 }?.let { duration -> observedDuration.value = duration }
                if (it == PlayerFact.Failed || it == PlayerFact.ControlFailed) failed = true
            }, commands = commands)
        }
        LaunchedEffect(Unit) {
            val fields = mutableMapOf<String, JsonElement>("passed" to JsonPrimitive(false),
                "measurement" to JsonPrimitive("KMP DesktopDrawCompleted; three distinct destination images after seek"))
            var worker: TorrentRuntimePort? = null
            var handle: String? = null
            var renewal: Job? = null
            suspend fun call(op: String): JsonObject = withContext(Dispatchers.IO) {
                requireNotNull(worker).call(buildJsonObject { put("op", op); handle?.let { put("handle", it) }; if (op == "renew") put("authority_ms", 60000) })
            }
            val timeout = specification["timeout_ms"]?.jsonPrimitive?.longOrNull ?: 90_000
            val startupOnly = specification["startup_only"]?.jsonPrimitive?.booleanOrNull == true
            fields["startup_only"] = JsonPrimitive(startupOnly)
            if (startupOnly) fields["measurement"] = JsonPrimitive("KMP DesktopDrawCompleted; cold first rendered image")
            try {
                val start = System.nanoTime()
                val url = if (specification["source"] != null) {
                    worker = withContext(Dispatchers.IO) {
                        specification["worker"]?.jsonPrimitive?.content?.let {
                            DesktopTorrentWorker(listOf(it), stderrLog = result.parent.resolve("worker.stderr"))
                        }
                            ?: requireNotNull(platformTorrentRuntime()).connect()
                    }
                    withContext(Dispatchers.IO) {
                        val opened = requireNotNull(worker).call(buildJsonObject {
                            put("op", "open"); put("config", buildJsonObject { put("cache_dir", specification.getValue("cache")); put("cache_bytes", 2L shl 30) })
                        }, 8_000)
                        check(opened["ok"]?.jsonPrimitive?.booleanOrNull == true)
                        val prepared = requireNotNull(worker).call(buildJsonObject { put("op", "prepare"); put("source", specification.getValue("source")); put("authority_ms", 60000) })
                        check(prepared["ok"]?.jsonPrimitive?.booleanOrNull == true)
                        handle = prepared.getValue("handle").jsonPrimitive.content
                    }
                    renewal = launch { while (isActive) { delay(15_000); check(call("renew")["ok"]?.jsonPrimitive?.booleanOrNull == true) } }
                    withTimeout(timeout) {
                        var ready: String? = null
                        while (ready == null) {
                            val reply = call("observe")
                            check(reply["progress"]?.jsonObject?.get("error") == null)
                            ready = reply["media_url"]?.jsonPrimitive?.content
                            if (ready == null) delay(50)
                        }
                        ready
                    }
                } else if (specification["reference_reply"] != null) {
                    val reply = Path.of(specification.getValue("reference_reply").jsonPrimitive.content)
                    Files.writeString(Path.of(specification.getValue("reference_ready").jsonPrimitive.content), "ready")
                    withTimeout(timeout) { while (!Files.isRegularFile(reply)) delay(20) }
                    Json.parseToJsonElement(Files.readString(reply)).jsonObject.getValue("url").jsonPrimitive.content
                } else specification.getValue("url").jsonPrimitive.content
                fields["endpoint_seconds"] = JsonPrimitive((System.nanoTime()-start)/1e9)
                input = PlaybackInput("torrent-benchmark", url, localTorrent = true, diagnostics = { samples.add("at_ms=${(System.nanoTime()-start)/1_000_000} $it") })
                withTimeout(timeout) { frames.first { it.first > 0 } }
                fields["first_frame_seconds"] = JsonPrimitive((firstFrameAt.get()-start)/1e9)
                if (worker != null) check(call("first_frame")["ok"]?.jsonPrimitive?.booleanOrNull == true)
                if (specification["smoothness_smoke"]?.jsonPrimitive?.booleanOrNull == true) {
                    fields["measurement"] = JsonPrimitive("Desktop distinct-image draws; 45s start, 95% seek + 15s, backward seek + 15s")
                    val phases = mutableListOf<JsonElement>()
                    suspend fun watch(name: String, seconds: Int) {
                        val begun = System.nanoTime()
                        val before = frames.value
                        var worstAge = 0L
                        var stalls = 0
                        var stale = false
                        for (tick in 0 until seconds * 10) {
                            delay(100)
                            val age = ((System.nanoTime() - frames.value.third) / 1_000_000).coerceAtLeast(0)
                            worstAge = maxOf(worstAge, age)
                            if (age > 500 && !stale) stalls++
                            stale = age > 500
                            if (failed) break
                        }
                        val after = frames.value
                        val wall = (System.nanoTime() - begun) / 1e9
                        val fps = (after.first - before.first) / wall
                        val advance = after.second - before.second
                        val smooth = stalls == 0 && fps >= 20 && advance >= seconds * 800L && !failed
                        phases.add(buildJsonObject {
                            put("phase", name); put("wall_seconds", wall)
                            put("drawn_frames", after.first - before.first); put("rendered_fps", fps)
                            put("start_position_ms", before.second); put("end_position_ms", after.second)
                            put("position_advanced_ms", advance); put("max_sampled_frame_age_ms", worstAge)
                            put("freeze_events_over_500ms", stalls); put("smooth", smooth)
                        })
                        fields["playback_phases"] = JsonArray(phases)
                        check(!failed)
                    }
                    suspend fun seek(name: String, target: Long) {
                        val before = frames.value.first
                        val begun = System.nanoTime()
                        fields[name + "_target_ms"] = JsonPrimitive(target)
                        commands.emit(PlayerCommand.Seek(target))
                        withTimeout(timeout) {
                            frames.filter { it.first > before && it.second in target..target + 3000 }.take(3).collect()
                        }
                        fields[name + "_seconds"] = JsonPrimitive((System.nanoTime() - begun) / 1e9)
                        check(!failed)
                    }
                    watch("start", 45)
                    val movieDuration = withTimeout(5000) { observedDuration.first { it > 120_000 } }
                    fields["observed_duration_ms"] = JsonPrimitive(movieDuration)
                    seek("near_end", movieDuration * 95 / 100)
                    watch("near_end", 15)
                    seek("backward", 30_000)
                    watch("backward", 15)
                    fields["smoke_smooth"] = JsonPrimitive(phases.all { it.jsonObject.getValue("smooth").jsonPrimitive.boolean })
                    check(fields.getValue("smoke_smooth").jsonPrimitive.boolean)
                } else {
                // Keep the same player alive; no decoder relaunch or synthetic position-only success.
                if (!startupOnly) delay(1000)
                val seekScreen = specification["seek_screen"]?.jsonPrimitive?.booleanOrNull == true
                val forwardTarget = if (seekScreen) {
                    val duration = withTimeout(5000) { observedDuration.first { it > 120_000 } }
                    fields["observed_duration_ms"] = JsonPrimitive(duration)
                    duration * 95 / 100
                } else 120_000L
                for ((name, target) in if (startupOnly) emptyList() else listOf("forward" to forwardTarget, "backward" to 10_000L)) {
                    val before = frames.value.first
                    fields[name+"_target_ms"] = JsonPrimitive(target)
                    val seekStart = System.nanoTime()
                    commands.emit(PlayerCommand.Seek(target))
                    withTimeout(timeout) { frames.filter { it.first > before && it.second in target..target + 3000 }.take(3).collect() }
                    check(!failed)
                    fields[name+"_seconds"] = JsonPrimitive((System.nanoTime()-seekStart)/1e9)
                    if (seekScreen) {
                        val begun = System.nanoTime()
                        val initial = frames.value
                        var freezes = 0
                        var maxAge = 0L
                        repeat(30) {
                            delay(100)
                            val age = (System.nanoTime()-frames.value.third)/1_000_000
                            maxAge = maxOf(maxAge, age)
                            if (age > 500) freezes++
                        }
                        val after = frames.value
                        val wall = (System.nanoTime()-begun)/1e9
                        val fps = (after.first-initial.first)/wall
                        fields[name+"_playback"] = buildJsonObject {
                            put("seconds",wall);put("rendered_fps",fps);put("freeze_samples",freezes);put("max_frame_age_ms",maxAge)
                            put("position_advanced_ms",after.second-initial.second)
                        }
                        check(freezes == 0 && fps >= 20 && after.second-initial.second >= 2400)
                    } else delay(500)
                }
                val duration = specification["sustained_seconds"]?.jsonPrimitive?.longOrNull ?: 0
                if (duration > 0) {
                    var previous = frames.value.first
                    var stalls = 0
                    repeat(duration.toInt()) { delay(1000); val current = frames.value.first; if (current == previous) stalls++; previous = current }
                    fields["seconds_without_new_frames"] = JsonPrimitive(stalls)
                }
                }
                check(!failed)
                fields["passed"] = JsonPrimitive(true)
            } catch (error: Exception) {
                fields["error"] = JsonPrimitive("playback_measurement_failed")
                fields["measurement_error_type"] = JsonPrimitive(error.javaClass.simpleName)
            }
            finally {
              withContext(NonCancellable) {
                renewal?.cancelAndJoin()
                input = null
                if (worker != null) {
                    var retirement = "joined"
                    val retired = runCatching {
                        suspend fun retire(op: String): Boolean {
                            val reply = call(op)
                            if (reply["terminate"]?.jsonPrimitive?.booleanOrNull == true) {
                                check(reply["ok"]?.jsonPrimitive?.booleanOrNull == false)
                                check(reply["error"]?.jsonPrimitive?.content == "settlement_timeout")
                                retirement = "forced"
                                return true
                            }
                            check(reply["ok"]?.jsonPrimitive?.booleanOrNull == true)
                            return false
                        }
                        val forced = handle != null && retire("close")
                        if (!forced) retire("shutdown")
                    }.isSuccess
                    withContext(Dispatchers.IO) { worker?.terminate() }
                    val exited = (worker as? DesktopTorrentWorker)?.processHasExited() == true
                    (worker as? DesktopTorrentWorker)?.exitCodeOrNull()?.let { fields["worker_exit_code"] = JsonPrimitive(it) }
                    fields["worker_retirement"] = JsonPrimitive(if (retired && exited) retirement else "failed")
                    fields["worker_process_exited"] = JsonPrimitive(exited)
                    if (!retired || !exited) {
                        fields["passed"] = JsonPrimitive(false)
                        fields["retirement_error"] = JsonPrimitive("worker_shutdown_failed")
                        if (fields["error"] == null) fields["error"] = JsonPrimitive("worker_shutdown_failed")
                    }
                }
                fields["presented_frames"] = JsonPrimitive(frames.value.first)
                fields["player_samples"] = JsonArray(samples.map(::JsonPrimitive))
                Files.writeString(result, JsonObject(fields).toString()+"\n", CREATE, APPEND)
                exitApplication()
              }
            }
        }
    }
        }
}
