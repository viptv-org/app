package org.viptv.experiment

import android.os.Bundle
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.ViewTreeObserver
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import org.json.JSONObject
import org.viptv.app.state.*
import kotlin.coroutines.resume
import java.net.HttpURLConnection
import java.net.URI

/** Full application measurements on the named disposable Android TV AVD only. */
class PerformanceQaActivity : MainActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var frames: PerformanceQaFrames
    private lateinit var controller: ApplicationController
    private val marks = JSONObject()
    private val processStart = Process.getStartUptimeMillis()
    private val entered = SystemClock.uptimeMillis()
    private fun elapsed() = SystemClock.uptimeMillis() - processStart
    private fun mark(name: String, millis: Long = elapsed()) {
        marks.put(name, millis)
        Log.i("VIPTV_PERFORMANCE_QA", "$name=$millis")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val origin = requireNotNull(intent.getStringExtra("qa_origin"))
        val parsed = android.net.Uri.parse(origin)
        require(parsed.scheme == "https" && parsed.host == "127.0.0.1" && parsed.port > 0)
        controller = ViewModelProvider(this)[ApplicationModel::class.java].controller
        frames = PerformanceQaFrames(window)
        mark("activity_enter_ms", entered - processStart)
        var first = true
        val draw = object : ViewTreeObserver.OnDrawListener {
            override fun onDraw() { if (first) { first = false; mark("first_draw_ms") } }
        }
        window.decorView.viewTreeObserver.addOnDrawListener(draw)
        scope.launch {
            try { qualify(origin, intent.getStringExtra("qa_mode") ?: "cold-empty") }
            catch (error: TimeoutCancellationException) { Log.e("VIPTV_PERFORMANCE_QA", "FAIL Timed out waiting for an app draw/state", error) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Log.e("VIPTV_PERFORMANCE_QA", "FAIL ${error.javaClass.simpleName}: ${error.message}", error) }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Test driver finishes this Activity without killing its process, then
        // verifies a genuinely new Activity/model with retained secure session.
        if (intent.getBooleanExtra("qa_finish", false)) finish()
    }
    private suspend fun await(predicate: () -> Boolean) = withTimeout(45_000) {
        while (!predicate()) { check(controller.state.value.error == null) { controller.state.value.error.orEmpty() }; delay(10) }
    }
    private suspend fun drawn(predicate: () -> Boolean): Long = withTimeout(45_000) {
        suspendCancellableCoroutine { continuation ->
            var done = false
            val view = window.decorView
            val listener = object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    if (!done && predicate()) {
                        done = true
                        val at = SystemClock.uptimeMillis()
                        view.post {
                            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(this)
                            if (continuation.isActive) continuation.resume(at)
                        }
                    }
                }
            }
            view.viewTreeObserver.addOnDrawListener(listener)
            continuation.invokeOnCancellation { view.post { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnDrawListener(listener) } }
            view.invalidate()
        }
    }
    private suspend fun usefulHome(): Long = drawn {
        val state = controller.state.value
        state.screen == Screen.Home && state.home != null && !state.starting &&
            PerformanceQaSemantics.texts(window.decorView).any { it.contains("Lifecycle fixture") } &&
            PerformanceQaSemantics.interactive(window.decorView)
    }
    private fun press(code: Int) {
        val now = SystemClock.uptimeMillis()
        window.decorView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        window.decorView.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }
    private suspend fun qualify(origin: String, mode: String) {
        val shell = drawn {
            val starting = controller.state.value.starting
            PerformanceQaSemantics.interactive(window.decorView) && (!starting ||
                (PerformanceQaSemantics.texts(window.decorView).any { it == "Getting VIPTV ready…" } &&
                    PerformanceQaSemantics.focusedText(window.decorView).contains("Change connection")))
        }
        mark("interactive_shell_ms", shell - processStart)
        mark("interactive_shell_from_activity_ms", shell - entered)
        if (mode == "cancel-restored") {
            check(controller.state.value.starting) { "Restore completed before cancellation could be qualified" }
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            await { !controller.state.value.starting && !controller.state.value.busy && controller.state.value.screen == Screen.Connect }
            drawn { PerformanceQaSemantics.texts(window.decorView).any { it == "Sign in" } }
            delay(1100)
            check(controller.state.value.screen == Screen.Connect && controller.state.value.home == null && controller.state.value.profiles.isEmpty()) {
                "Canceled restoration later exposed private state"
            }
            marks.put("restore_canceled", true)
        } else if (mode == "cold-empty") {
            await { !controller.state.value.starting && !controller.state.value.busy }
            check(controller.state.value.screen == Screen.Connect)
            controller.connect(origin, "lifecycle-fixture", "synthetic-fixture-password")
            await { controller.state.value.screen == Screen.Profiles && !controller.state.value.busy }
            drawn { PerformanceQaSemantics.texts(window.decorView).any { it.contains("First fixture") } }
            delay(250)
            val profile = SystemClock.uptimeMillis()
            press(KeyEvent.KEYCODE_DPAD_CENTER)
            mark("profile_to_home_ms", usefulHome() - profile)
        } else {
            check(mode == "cold-restored" || mode == "warm-activity")
            val home = usefulHome()
            mark("useful_home_ms", home - processStart)
            mark("useful_home_from_activity_ms", home - entered)
        }
        reportFullyDrawn()
        if (mode == "cold-restored") calendar()
        delay(250)
        val result = JSONObject().put("mode", mode).put("marks_ms", marks).put("frames", frames.snapshot())
        withContext(Dispatchers.IO) {
            filesDir.resolve("performance-$mode.json").writeText(result.toString())
            val report = URI(origin + "/qa/performance").toURL().openConnection() as HttpURLConnection
            try {
                report.requestMethod = "POST"; report.doOutput = true
                report.connectTimeout = 5000; report.readTimeout = 5000
                report.setRequestProperty("Content-Type", "application/json")
                report.outputStream.use { it.write(result.toString().encodeToByteArray()) }
                check(report.responseCode == 200) { "Fixture refused performance report" }
            } finally { report.disconnect() }
        }
        Log.i("VIPTV_PERFORMANCE_QA", "PASS mode=$mode file=performance-$mode.json")
    }
    private suspend fun calendar() {
        frames.phase = "calendar-load"
        val started = SystemClock.uptimeMillis()
        controller.navigate(Screen.Calendar)
        drawn { PerformanceQaSemantics.texts(window.decorView).any { it.contains("Calendar fixture 0000") } }
        mark("calendar_first_page_ms", SystemClock.uptimeMillis() - started)
        check(PerformanceQaSemantics.focus(window.decorView, "Today")) { "Today date could not receive focus" }
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        await { PerformanceQaSemantics.focusedText(window.decorView).contains("Calendar fixture 0000") }
        await { !controller.state.value.busy && controller.state.value.calendarEntries.size == 1200 }
        check(PerformanceQaSemantics.focusedText(window.decorView).contains("Calendar fixture 0000")) { "Later pages moved calendar focus" }
        delay(300)
        frames.phase = "calendar-scroll"
        val scroll = SystemClock.uptimeMillis()
        repeat(80) { press(KeyEvent.KEYCODE_DPAD_DOWN); delay(100) }
        delay(300)
        frames.phase = "settled"
        val selected = PerformanceQaSemantics.focusedText(window.decorView)
        val index = Regex("Calendar fixture (\\d{4})").find(selected)?.groupValues?.get(1)?.toIntOrNull()
        check(index != null && index >= 70) { "Calendar focus did not advance through rows: $selected" }
        mark("calendar_scroll_ms", SystemClock.uptimeMillis() - scroll)
        marks.put("calendar_final_index", index)
        frames.phase = "warm-home"
        val warm = SystemClock.uptimeMillis()
        controller.navigate(Screen.Home)
        mark("warm_route_home_ms", usefulHome() - warm)
    }
    override fun onDestroy() { scope.cancel(); if (::frames.isInitialized) frames.close(); super.onDestroy() }
}
