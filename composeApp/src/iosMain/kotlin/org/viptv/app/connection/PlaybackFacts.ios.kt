package org.viptv.app.connection

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import platform.UIKit.UIScreen

@OptIn(ExperimentalForeignApi::class)
internal actual fun playbackClientFacts(): JsonObject = buildJsonObject {
    put("platform", "ios")
    // Force-gateway, browser-parity: AVPlayer consumes gateway HLS renditions
    // (h264/aac) exactly like the browser's hls.js path.
    put("can_play_direct", false)
    put("max_width", UIScreen.mainScreen.nativeBounds.useContents { size.width.toInt() })
    put("max_height", UIScreen.mainScreen.nativeBounds.useContents { size.height.toInt() })
    putJsonArray("video_codecs") { add(JsonPrimitive("h264")) }
    putJsonArray("audio_codecs") { add(JsonPrimitive("aac")) }
}
