@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.connection

import kotlinx.serialization.json.*

internal actual fun playbackClientFacts(): JsonObject = Json.parseToJsonElement(browserPlaybackFacts()).jsonObject

@JsFun("""() => {
 const video = document.createElement('video');
 return JSON.stringify({platform:'web',can_play_direct:false,
 max_width:Math.round(screen.width*devicePixelRatio),max_height:Math.round(screen.height*devicePixelRatio),
 video_codecs:video.canPlayType('video/mp4; codecs="avc1.42E01E"')?['h264']:[],
 audio_codecs:video.canPlayType('audio/mp4; codecs="mp4a.40.2"')?['aac']:[]});
}""")
private external fun browserPlaybackFacts(): String
