@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.player

import kotlinx.serialization.json.*

internal fun readHlsAudioTracks(hls: Hls): List<PlaybackTrack> {
    val array = Json.parseToJsonElement(hlsAudioTracksJson(hls)).jsonArray
    return array.map { track ->
        val item = track.jsonObject
        browserAudioTrack(item.getValue("id").jsonPrimitive.int, item["name"]?.jsonPrimitive?.contentOrNull,
            item["lang"]?.jsonPrimitive?.contentOrNull, item.getValue("current").jsonPrimitive.boolean)
    }
}

@JsFun("""(hls) => { const tracks = hls.audioTracks || []; const current = hls.audioTrack;
 return JSON.stringify(tracks.map((t, i) => ({ id: i, name: t.name || null, lang: t.lang || null, current: i === current }))); }""")
internal external fun hlsAudioTracksJson(hls: Hls): String

@JsFun("""(hls, id) => { hls.audioTrack = id; }""")
internal external fun selectHlsAudioTrack(hls: Hls, id: Int)

@JsFun("""(hls, event, callback) => { const listener = () => callback(); hls.on(event, listener); return listener; }""")
internal external fun listenForHlsEvent(hls: Hls, event: String, callback: () -> Unit): JsAny

@JsFun("""(hls, event, listener) => { hls.off(event, listener); }""")
internal external fun removeHlsListener(hls: Hls, event: String, listener: JsAny)