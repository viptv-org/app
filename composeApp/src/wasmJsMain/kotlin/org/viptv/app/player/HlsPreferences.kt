@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.player

@JsFun("""(audio, subtitle, startPosition) => ({startPosition, audioPreference: audio ? {lang: audio} : undefined, subtitlePreference: subtitle ? {lang: subtitle} : undefined})""")
internal external fun hlsPreferences(audio: String, subtitle: String, startPosition: Double): JsAny
