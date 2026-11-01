package org.viptv.app.crash

import android.content.Context
import java.io.File
import kotlinx.serialization.json.Json

object AndroidCrashReports {
    private var appContext: Context? = null
    private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun attach(context: Context) { appContext = context.applicationContext }

    private fun write(file: File, value: String) = try { file.writeText(value) } catch (_: Exception) { }
    private fun text(file: File): String? = try { if (file.isFile) file.readText() else null } catch (_: Exception) { null }

    internal fun storeReport(report: CrashReport) {
        val dir = appContext?.filesDir ?: return
        write(File(dir, "crash-report.json"), codec.encodeToString(CrashReport.serializer(), report))
    }

    internal fun pendingReport(): CrashReport? {
        val dir = appContext?.filesDir ?: return null
        val stored = readReport(File(dir, "crash-report.json"))
        val marker = playbackMarker(File(dir, "playback-marker.json"))
        return stored ?: marker?.let { CrashReport(playback = it, nativeDuringPlaybackStart = true) }
    }

    internal fun deletePending() {
        val dir = appContext?.filesDir ?: return
        try { File(dir, "crash-report.json").delete(); File(dir, "playback-marker.json").delete() } catch (_: Exception) { }
    }

    internal fun writePlaybackMarker(attempt: PlaybackAttempt) {
        val dir = appContext?.filesDir ?: return
        write(File(dir, "playback-marker.json"), codec.encodeToString(PlaybackAttempt.serializer(), attempt))
    }

    internal fun clearPlaybackMarker() {
        val dir = appContext?.filesDir ?: return
        try { File(dir, "playback-marker.json").delete() } catch (_: Exception) { }
    }

    private fun readReport(file: File): CrashReport? = text(file)?.let { raw ->
        try { codec.decodeFromString(CrashReport.serializer(), raw) } catch (_: Exception) { null }
    }
    private fun playbackMarker(file: File): PlaybackAttempt? = text(file)?.let { raw ->
        try { codec.decodeFromString(PlaybackAttempt.serializer(), raw) } catch (_: Exception) { null }
    }
}

internal actual fun platformStoreCrashReport(report: CrashReport) = AndroidCrashReports.storeReport(report)
internal actual fun platformPendingCrashReport(): CrashReport? = AndroidCrashReports.pendingReport()
internal actual fun platformDeletePendingCrashReport() = AndroidCrashReports.deletePending()
internal actual fun platformWritePlaybackMarker(attempt: PlaybackAttempt) = AndroidCrashReports.writePlaybackMarker(attempt)
internal actual fun platformClearPlaybackMarker() = AndroidCrashReports.clearPlaybackMarker()