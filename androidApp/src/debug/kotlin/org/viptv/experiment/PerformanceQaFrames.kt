package org.viptv.experiment

import android.os.Handler
import android.os.HandlerThread
import android.os.Build
import android.view.FrameMetrics
import android.view.Window
import org.json.JSONArray
import org.json.JSONObject

/** Passive platform metrics; no semantics scans or per-frame logging on the UI thread. */
internal class PerformanceQaFrames(private val window: Window) {
    private val thread = HandlerThread("viptv-qa-frames").apply { start() }
    @Volatile var phase = "startup"
    private val samples = mutableMapOf<String, MutableList<LongArray>>()
    private val listener = Window.OnFrameMetricsAvailableListener { _, frame, dropped ->
        val sample = longArrayOf(frame.getMetric(FrameMetrics.TOTAL_DURATION),
            frame.getMetric(FrameMetrics.FIRST_DRAW_FRAME), dropped.toLong(),
            if (Build.VERSION.SDK_INT >= 31) frame.getMetric(FrameMetrics.DEADLINE) else -1,
            frame.getMetric(FrameMetrics.VSYNC_TIMESTAMP))
        synchronized(samples) { samples.getOrPut(phase) { mutableListOf() }.add(sample) }
    }
    init { window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
    fun snapshot(): JSONObject = synchronized(samples) {
        JSONObject().apply {
            for ((key, values) in samples) put(key, JSONArray().apply {
                values.forEach { put(JSONArray(it.toList())) }
            })
        }
    }
    fun close() { window.removeOnFrameMetricsAvailableListener(listener); thread.quitSafely() }
}
