package org.viptv.app.connection

import kotlinx.serialization.json.*

internal expect fun nativePlaybackPlatform(): String
internal expect fun nativeDisplayBounds(): Pair<Int, Int>

internal actual fun playbackClientFacts(): JsonObject = buildJsonObject {
    val (width, height) = nativeDisplayBounds()
    put("platform", nativePlaybackPlatform()); put("can_play_direct", true)
    put("max_width", width); put("max_height", height)
    // Conservative codecs implemented by the pinned FFmpeg backend on both native platforms.
    put("video_codecs", JsonArray(listOf(JsonPrimitive("h264"))))
    put("audio_codecs", JsonArray(listOf(JsonPrimitive("aac"))))
}
