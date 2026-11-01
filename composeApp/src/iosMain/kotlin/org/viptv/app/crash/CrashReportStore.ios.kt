package org.viptv.app.crash

internal actual fun platformStoreCrashReport(report: CrashReport) = Unit
internal actual fun platformPendingCrashReport(): CrashReport? = null
internal actual fun platformDeletePendingCrashReport() = Unit
internal actual fun platformWritePlaybackMarker(attempt: PlaybackAttempt) = Unit
internal actual fun platformClearPlaybackMarker() = Unit