package org.viptv.app.player

// The host's HLS format also covers signed/opaque endpoints without a file extension.
internal fun PlaybackInput.isHlsPlaylist(): Boolean =
    mimeType?.substringBefore(';')?.trim()?.lowercase() in setOf(
        "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl"
    ) || uri.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true)
