package org.viptv.app.components

import androidx.compose.runtime.withFrameNanos

// A visible timeout counts the frames the viewer is shown: delay() runs on the dispatcher's clock, which no scene renders.
internal suspend fun frameDelay(millis: Long) {
    var remaining = millis * 1_000_000L
    var last = withFrameNanos { it }
    while (remaining > 0) {
        val now = withFrameNanos { it }
        remaining -= (now - last).coerceAtLeast(0)
        last = now
    }
}
