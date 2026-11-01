package org.viptv.app.crash

import kotlin.concurrent.Volatile

object CrashFacts {
    @Volatile var lastScreen: String? = null
    @Volatile var playbackAttempt: PlaybackAttempt? = null
}

internal expect fun platformStoreCrashReport(report: CrashReport)
internal expect fun platformPendingCrashReport(): CrashReport?
internal expect fun platformDeletePendingCrashReport()
internal expect fun platformWritePlaybackMarker(attempt: PlaybackAttempt)
internal expect fun platformClearPlaybackMarker()

object CrashReportStore {
    fun storeReport(report: CrashReport) = platformStoreCrashReport(report.sanitized())
    fun pendingReport(): CrashReport? = platformPendingCrashReport()
    fun deletePending() = platformDeletePendingCrashReport()
    fun writePlaybackMarker(attempt: PlaybackAttempt) = platformWritePlaybackMarker(attempt.sanitized())
    fun clearPlaybackMarker() = platformClearPlaybackMarker()
}