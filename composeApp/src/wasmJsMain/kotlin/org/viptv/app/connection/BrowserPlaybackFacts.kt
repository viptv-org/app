@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.connection

import kotlinx.serialization.json.*

internal actual fun playbackClientFacts(): JsonObject = Json.parseToJsonElement(browserPlaybackFacts()).jsonObject

@JsFun("""() => {
 const clamp = n => Math.min(16384, Math.max(2, Math.round(n) || 1920));
 return JSON.stringify({platform:'web',can_play_direct:false,
 max_width:clamp(screen.width*devicePixelRatio),max_height:clamp(screen.height*devicePixelRatio),
 video_codecs:['h264'],audio_codecs:['aac']});
}""")
private external fun browserPlaybackFacts(): String
