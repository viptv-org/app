package org.viptv.app.player

/** Gateway media begins at an already-applied seek; history and controls use the original timeline. */
internal fun PlaybackInput.mediaProgress(positionMillis: Long, observedDurationMillis: Long?): PlayerFact.Progress {
    val position = positionMillis.coerceAtLeast(0) + timelineOffsetMillis.coerceAtLeast(0)
    val duration = durationMillis ?: observedDurationMillis?.let { it + timelineOffsetMillis.coerceAtLeast(0) }
    return PlayerFact.Progress(position, duration)
}
