package org.viptv.experiment

import android.app.UiModeManager
import android.content.res.Configuration
import android.util.Log
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import org.json.JSONObject
import org.viptv.app.connection.AndroidSecrets
import org.viptv.app.state.*
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URI
import kotlin.math.abs

/** Retained test driver; all application behavior remains in MainActivity/ApplicationController. */
@UnstableApi
class LifecycleQaProbe : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var current = WeakReference<MainActivity>(null)
    private var model: ApplicationModel? = null
    private var started = false
    private var origin = ""
    private var sustainMillis = 0L
    private var seekReplacements = 0
    private var finalCapture = false
    fun attach(activity: MainActivity, value: ApplicationModel, url: String, sustain: Long = 0, captureFinal: Boolean = false) {
        require(model == null || model === value) { "Application model replaced during recreation" }
        current = WeakReference(activity); model = value; origin = url
        if (started) return
        started = true
        sustainMillis = sustain.coerceIn(0, 2_700_000)
        finalCapture = captureFinal || sustainMillis >= 2_400_000
        scope.launch { try { qualify(value.controller) } catch (error: Exception) {
            Log.e("VIPTV_LIFECYCLE_QA", "FAIL ${error.javaClass.simpleName}: ${error.message}", error)
        } }
    }
    private fun activity() = requireNotNull(current.get())
    private suspend fun await(controller: ApplicationController, predicate: () -> Boolean) = withTimeout(45_000) {
        while (!predicate()) { check(controller.state.value.error == null) { "App error: ${controller.state.value.error}" }; delay(25) }
    }
    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val connection = URI(origin + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.connectTimeout = 5000; connection.readTimeout = 5000
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            check(connection.responseCode == 200) { "Fixture request refused" }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }
    private fun count(status: JSONObject, path: String) = status.getJSONObject("counts").optInt(path)
    private suspend fun authorityCounts() {
        val status = request("/qa/status")
        check(count(status, "POST /api/auth/device/login") == 1)
        check(count(status, "POST /api/auth/device/refresh") == 1) { "Refresh replayed" }
        check(count(status, "GET /api/v3/bootstrap") == 4) { "Bootstrap replayed" }
        check(count(status, "POST /api/auth/profile") == 1) { "Profile selection replayed" }
    }
    private suspend fun recreate(controller: ApplicationController) {
        val previous = activity()
        previous.recreate()
        await(controller) { current.get() !== previous && activity().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        check(ViewModelProvider(activity())[ApplicationModel::class.java] === model)
        check(requireNotNull(model).controller === controller)
        delay(350)
        authorityCounts()
    }
    private fun position(controller: ApplicationController) = requireNotNull(LifecycleQaInspector.progress(controller)).positionMillis
    private fun window(player: ExoPlayer) = player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window()).positionInFirstPeriodMs
    private fun checkClock(controller: ApplicationController, player: ExoPlayer) {
        val input = requireNotNull(controller.state.value.playback)
        val expected = player.currentPosition + window(player) + input.timelineOffsetMillis
        check(abs(position(controller) - expected) <= 250) { "Title clock differs from actual renderer/window/seek origin" }
    }
    private suspend fun homeCycle(controller: ApplicationController, wasPaused: Boolean) {
        val owner = activity()
        val input = controller.state.value.playback
        val player = requireNotNull(LifecycleQaInspector.player(owner))
        Log.i("VIPTV_LIFECYCLE_QA", "await_home paused=$wasPaused")
        await(controller) { !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        delay(350)
        check(controller.state.value.paused)
        val stopped = position(controller)
        delay(400)
        check(abs(position(controller) - stopped) <= 20) { "Playback continued in background" }
        Log.i("VIPTV_LIFECYCLE_QA", "background_paused position_ms=$stopped was_paused=$wasPaused")
        await(controller) { activity().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        check(activity() === owner) { "Launcher recreated the task instead of returning" }
        check(controller.state.value.playback === input && controller.state.value.selectedProfile == "one" && controller.state.value.screen == Screen.Player)
        check(LifecycleQaInspector.player(activity()) === player)
        check(controller.state.value.paused == wasPaused) { "Foreground pause intent differs: expected=$wasPaused actual=${controller.state.value.paused}" }
        if (!wasPaused) await(controller) { position(controller) > stopped + 100 }
        authorityCounts()
    }
    private suspend fun pauseOnly(controller: ApplicationController, wasPaused: Boolean) {
        val owner = activity() as LifecycleQaActivity
        val player = requireNotNull(LifecycleQaInspector.player(owner))
        val input = controller.state.value.playback
        val beforeStarts = owner.starts; val beforeStops = owner.stops
        val beforePauses = owner.pauses; val beforeResumes = owner.resumes
        owner.startActivity(android.content.Intent(owner, PauseOnlyQaActivity::class.java))
        await(controller) { owner.pauses == beforePauses + 1 && controller.state.value.paused && !player.playWhenReady }
        check(owner.lifecycle.currentState == Lifecycle.State.STARTED)
        delay(250)
        val stopped = position(controller)
        delay(300)
        check(abs(position(controller) - stopped) <= 20) { "Pause-only overlay did not suspend renderer" }
        await(controller) { owner.resumes == beforeResumes + 1 && owner.lifecycle.currentState == Lifecycle.State.RESUMED }
        check(owner.starts == beforeStarts && owner.stops == beforeStops) { "Probe did not exercise pause/resume without stop/start" }
        check(controller.state.value.playback === input && controller.state.value.paused == wasPaused)
        check(LifecycleQaInspector.player(owner) === player && controller.state.value.screen == Screen.Player)
        if (!wasPaused) await(controller) { player.playWhenReady && position(controller) > stopped + 100 }
        else { delay(300); check(!player.playWhenReady && abs(position(controller) - stopped) <= 20) }
        authorityCounts()
        Log.i("VIPTV_LIFECYCLE_QA", "pause_only=true was_paused=$wasPaused pauses=${owner.pauses-beforePauses} resumes=${owner.resumes-beforeResumes} stops=${owner.stops-beforeStops} starts=${owner.starts-beforeStarts} position_ms=${position(controller)}")
    }
    private suspend fun sustain(controller: ApplicationController, initialPlayer: ExoPlayer) {
        var player = initialPlayer
        val initialOrigin = requireNotNull(controller.state.value.playback).timelineOffsetMillis + window(player)
        val startedAt = android.os.SystemClock.elapsedRealtime()
        var samples = 0
        var seeks = 0
        Log.i("VIPTV_LIFECYCLE_QA", "sustain_begin=true requested_ms=$sustainMillis started_uptime_ms=$startedAt")
        while (android.os.SystemClock.elapsedRealtime() - startedAt < sustainMillis) {
            var input = requireNotNull(controller.state.value.playback)
            check(LifecycleQaInspector.player(activity()) === player)
            check(controller.state.value.presentation != null && player.playerError == null)
            check(player.playbackParameters.speed == 1f) { "Endurance must run at normal playback speed" }
            if (sustainMillis >= 2_400_000 && samples > 0 && samples % 60 == 0) homeCycle(controller, false)
            checkClock(controller, player)
            if (samples % 6 == 0) {
                controller.pausePlayback()
                await(controller) { !player.playWhenReady }
                val target = input.timelineOffsetMillis + window(player) + if (seeks % 2 == 0) 8_000 else 16_000
                val managed = input.remoteSeek != null
                val oldPlayer = player
                LifecycleQaInspector.command(activity(), org.viptv.app.player.PlayerCommand.Seek(target))
                if (managed) {
                    await(controller) { controller.state.value.playback != null && controller.state.value.playback !== input &&
                        controller.state.value.presentation != null && LifecycleQaInspector.progress(controller) != null }
                    player = requireNotNull(LifecycleQaInspector.player(activity()))
                    val replacement = requireNotNull(controller.state.value.playback)
                    check(player !== oldPlayer && replacement.id != input.id) { "Managed seek did not replace lease/player" }
                    input = replacement
                    seekReplacements++
                } else check(controller.state.value.playback === input && LifecycleQaInspector.player(activity()) === oldPlayer)
                await(controller) { abs(position(controller) - target) <= 250 }
                check(controller.state.value.paused && controller.state.value.screen == Screen.Player && controller.state.value.selectedProfile == "one")
                checkClock(controller, player)
                delay(350)
                check(abs(position(controller) - target) <= 250) { "Paused seek moved" }
                Log.i("VIPTV_LIFECYCLE_QA", "seek_verified managed=$managed lease=${input.id} target_ms=$target position_ms=${position(controller)} input_offset_ms=${input.timelineOffsetMillis} window_ms=${window(player)}")
                controller.pausePlayback()
                await(controller) { player.playWhenReady && position(controller) > target + 100 }
                seeks++
            }
            val before = position(controller)
            delay(5000)
            check(position(controller) > before + 1000) { "Sustained renderer stalled" }
            checkClock(controller, player)
            samples++
            Log.i("VIPTV_LIFECYCLE_QA", "sustain elapsed_ms=${android.os.SystemClock.elapsedRealtime()-startedAt} position_ms=${position(controller)} window_ms=${window(player)} seeks=$seeks renderer_position_ms=${player.currentPosition} input_offset_ms=${input.timelineOffsetMillis} speed=${player.playbackParameters.speed} clock_error_ms=${position(controller)-(player.currentPosition+window(player)+input.timelineOffsetMillis)}")
        }
        val finalOrigin = requireNotNull(controller.state.value.playback).timelineOffsetMillis + window(player)
        check(finalOrigin > initialOrigin + 20_000) { "Fixture did not advance the title/window origin" }
        authorityCounts()
        Log.i("VIPTV_LIFECYCLE_QA", "sustain_pass=true wall_ms=${android.os.SystemClock.elapsedRealtime()-startedAt} samples=$samples seeks=$seeks replacements=$seekReplacements origin_delta_ms=${finalOrigin-initialOrigin} page_size=${android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)}")
        if (finalCapture) captureFinalPlayer(controller, player)
    }
    private suspend fun captureFinalPlayer(controller: ApplicationController, player: ExoPlayer) {
        val input = requireNotNull(controller.state.value.playback)
        if (!controller.state.value.paused) controller.pausePlayback()
        await(controller) { !player.playWhenReady }
        delay(400)
        val held = position(controller)
        val playerId = System.identityHashCode(player)
        val duration = requireNotNull(LifecycleQaInspector.progress(controller)?.durationMillis)
        val seconds = held / 1000
        val expected = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
        // Up reveals the existing TV controls; it neither resumes playback nor scrubs.
        LifecycleQaInspector.press(activity(), KeyEvent.KEYCODE_DPAD_UP)
        delay(350)
        val semantics = withTimeout(10_000) {
            while (true) {
                val value = LifecycleQaInspector.playerSemantics(activity())
                if ((0 until value.getJSONArray("texts").length()).any { value.getJSONArray("texts").getString(it) == expected } &&
                    value.getJSONArray("sliders").length() == 1) return@withTimeout value
                delay(50)
            }
            error("Unreachable")
        }
        val slider = semantics.getJSONArray("sliders").getJSONObject(0)
        check(abs(slider.getDouble("current") - held.toDouble() / duration) <= 0.00001) { "Actual UI slider differs from renderer title position" }
        check(slider.getDouble("min") == 0.0 && slider.getDouble("max") == 1.0)
        LifecycleQaInspector.flushProgress(controller)
        val saved = request("/qa/status").getJSONArray("progress")
        val savedPosition = saved.getJSONObject(saved.length()-1).getLong("position_millis")
        check(abs(savedPosition-held) <= 250) { "Paused emitted progress differs from renderer" }
        val captureId = "final-${android.os.SystemClock.elapsedRealtime()}"
        val snapshot = JSONObject().put("id",captureId).put("expected_text",expected).put("position_ms",held)
            .put("duration_ms",duration).put("renderer_title_ms",player.currentPosition+window(player)+input.timelineOffsetMillis)
            .put("progress_ms",savedPosition).put("player_id",playerId).put("app_pid",android.os.Process.myPid())
            .put("paused",true).put("semantics",semantics)
        request("/qa/capture", "POST", snapshot)
        Log.i("VIPTV_LIFECYCLE_QA", "capture_endurance_ready id=$captureId expected=$expected position_ms=$held player_id=$playerId duration_ms=$duration slider_current=${slider.getDouble("current")}")
        withTimeout(120_000) {
            while (!request("/qa/capture?id=$captureId").optBoolean("acknowledged")) {
                check(controller.state.value.paused && !player.playWhenReady)
                check(controller.state.value.playback === input && LifecycleQaInspector.player(activity()) === player)
                check(position(controller) == held) { "Paused final renderer position changed before capture acknowledgement" }
                checkClock(controller, player)
                delay(150)
            }
        }
        check(controller.state.value.paused && !player.playWhenReady && position(controller) == held)
        check(LifecycleQaInspector.player(activity()) === player && controller.state.value.playback === input)
        checkClock(controller, player)
        Log.i("VIPTV_LIFECYCLE_QA", "capture_endurance_acknowledged id=$captureId position_ms=$held player_id=$playerId still_paused=true same_player=true same_input=true")
    }

    private suspend fun qualify(controller: ApplicationController) {
        val report: (String) -> Unit = { line -> Log.i("VIPTV_LIFECYCLE_QA", "player $line") }
        ApplicationController::class.java.getDeclaredField("playbackDiagnostics").apply { isAccessible = true }.set(controller, report)
        check(activity().getSystemService(UiModeManager::class.java).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION)
        val pageSize = android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)
        withContext(Dispatchers.IO) {
            val database = activity().filesDir.resolve("runtime-page-qa.sqlite").absolutePath
            val local = org.viptv.app.connection.localConnection(database)
            val profile = try {
                local.bootstrap()
                local.createProfile("Runtime page $pageSize").also {
                    local.setProfilePin(it.id, "", "4826")
                    check(local.verifyProfilePin(it.id, "4826"))
                    check(!local.verifyProfilePin(it.id, "1111"))
                }
            } finally { local.close() }
            val reopened = org.viptv.app.connection.localConnection(database)
            try {
                check(reopened.bootstrap().profiles.any { it.id == profile.id })
                check(reopened.verifyProfilePin(profile.id, "4826"))
            } finally { reopened.close() }
        }
        Log.i("VIPTV_LIFECYCLE_QA", "native_reopen_pin=true page_size=$pageSize")
        await(controller) { !controller.state.value.starting && !controller.state.value.busy }
        controller.connect(origin, "lifecycle-fixture", "synthetic-fixture-password")
        await(controller) { controller.state.value.screen == Screen.Profiles && !controller.state.value.busy && controller.state.value.profiles.size == 2 }
        await(controller) { activity().hasWindowFocus() }
        delay(650)
        LifecycleQaInspector.press(activity(), KeyEvent.KEYCODE_DPAD_CENTER)
        await(controller) { controller.state.value.selectedProfile == "one" && controller.state.value.screen == Screen.Home && !controller.state.value.busy }
        Log.i("VIPTV_LIFECYCLE_QA", "initial_profile_focus=first")
        request("/qa/expire", "POST")
        controller.refresh()
        await(controller) { !controller.state.value.busy }
        val persisted = withContext(Dispatchers.IO) { AndroidSecrets.vault().get("remote-session-v1") }
        check(JSONObject(requireNotNull(persisted)).getString("refreshToken") == "fixture-refresh-2")
        authorityCounts()
        controller.navigate(Screen.Search); controller.search("Lifecycle")
        await(controller) { !controller.state.value.busy && controller.state.value.results.isNotEmpty() }
        controller.open(controller.state.value.results.first())
        await(controller) { !controller.state.value.busy && controller.state.value.detail != null }
        val detail = controller.state.value.detail
        recreate(controller)
        check(controller.state.value.screen == Screen.Details && controller.state.value.detail === detail && controller.state.value.selectedProfile == "one")
        controller.chooseSource(requireNotNull(detail).item)
        await(controller) { !controller.state.value.findingSources && controller.state.value.sources.isNotEmpty() && !controller.state.value.busy }
        controller.playSource(controller.state.value.sources.first())
        await(controller) { controller.state.value.playback != null && controller.state.value.presentation != null && LifecycleQaInspector.player(activity()) != null }
        val player = requireNotNull(LifecycleQaInspector.player(activity()))
        await(controller) { window(player) >= 4000 }
        controller.pausePlayback(); delay(400)
        val pausedPosition = position(controller)
        checkClock(controller, player)
        LifecycleQaInspector.press(activity(), KeyEvent.KEYCODE_DPAD_UP)
        delay(350)
        val seconds = pausedPosition / 1000
        Log.i("VIPTV_LIFECYCLE_QA", "capture_elapsed expected=${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')} position_ms=$pausedPosition window_offset_ms=${window(player)}")
        delay(4500)
        val input = controller.state.value.playback
        recreate(controller)
        check(controller.state.value.screen == Screen.Player && controller.state.value.playback === input && controller.state.value.paused)
        check(LifecycleQaInspector.player(activity()) === player)
        check(position(controller) == pausedPosition)
        checkClock(controller, player)
        pauseOnly(controller, true)
        homeCycle(controller, true)
        controller.pausePlayback()
        await(controller) { position(controller) > pausedPosition + 100 }
        pauseOnly(controller, false)
        homeCycle(controller, false)
        checkClock(controller, player)
        if (sustainMillis > 0) sustain(controller, player)
        val finalPlayer = requireNotNull(LifecycleQaInspector.player(activity()))
        checkClock(controller, finalPlayer)
        val finalClock = finalPlayer.currentPosition + window(finalPlayer) + requireNotNull(controller.state.value.playback).timelineOffsetMillis
        controller.closePlayer(); delay(600)
        val status = request("/qa/status")
        val opens = 1 + seekReplacements
        check(count(status, "POST /api/v2/playback") == opens)
        val counts = status.getJSONObject("counts")
        val keys = counts.keys().asSequence().toList()
        val frames = keys.filter { it.startsWith("POST /api/v2/playback/") && it.endsWith("/first-frame") }.sumOf { counts.getInt(it) }
        val closes = keys.filter { it.startsWith("DELETE /api/v2/playback/") }.sumOf { counts.getInt(it) }
        check(frames == (if (status.optString("delivery_kind") == "direct") 0 else opens))
        check(closes == opens)
        val events = status.getJSONArray("events")
        check(events.length() >= 5)
        check(events.getJSONObject(0).getLong("position_millis") in 600_000..600_250) { "Explicit title start skipped forward" }
        for (index in 0 until events.length()) check(events.getJSONObject(index).getLong("position_millis") >= 600_000)
        val progress = status.getJSONArray("progress")
        check(progress.length() > 0)
        val savedClock = progress.getJSONObject(progress.length() - 1).getLong("position_millis")
        check(abs(savedClock - finalClock) <= 250) { "Saved progress differs from final renderer title clock" }
        Log.i("VIPTV_LIFECYCLE_QA", "saved_clock_verified renderer_ms=$finalClock saved_ms=$savedClock leases=$opens")
        Log.i("VIPTV_LIFECYCLE_QA", "PASS profile_focus=true details_retained=true player_retained=true user_pause_retained=true background_paused=true foreground_resumed=true pause_only_playing=true pause_only_user_paused=true auth_refreshes=1 bootstraps=4 lease_opens=$opens title_clock=true")
    }
    override fun onCleared() { scope.cancel() }
}
