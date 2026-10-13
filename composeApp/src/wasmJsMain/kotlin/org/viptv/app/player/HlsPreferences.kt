@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.player

@JsFun("""(audio, subtitle) => ({audioPreference: audio ? {lang: audio} : undefined, subtitlePreference: subtitle ? {lang: subtitle} : undefined})""")
internal external fun hlsPreferences(audio: String, subtitle: String): JsAny
