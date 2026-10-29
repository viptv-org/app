package org.viptv.app.player

// Gateway media begins at an already-applied seek; history and controls use the original timeline.
internal fun PlaybackInput.mediaProgress(positionMillis: Long, observedDurationMillis: Long?, windowOffsetMillis: Long = 0): PlayerFact.Progress {
    // A rolling HLS playlist reports a position in its current window, whose origin advances as segments expire.
    val offset = timelineOffsetMillis.coerceAtLeast(0) + windowOffsetMillis.coerceAtLeast(0)
    val position = positionMillis.coerceAtLeast(0) + offset
    val duration = durationMillis ?: observedDurationMillis?.let { it + offset }
    return PlayerFact.Progress(position, duration)
}

// Renderer seeks are relative to the current playlist; server seeks keep title coordinates.
internal fun PlaybackInput.mediaSeekPosition(positionMillis: Long, windowOffsetMillis: Long = 0): Long =
    (positionMillis - timelineOffsetMillis.coerceAtLeast(0) - windowOffsetMillis.coerceAtLeast(0)).coerceAtLeast(0)
