@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.player

import org.w3c.dom.HTMLMediaElement

external interface HlsEvents : JsAny { val ERROR: String }

@JsModule("hls.js")
external class Hls : JsAny {
    constructor()
    constructor(options: JsAny)
    var subtitleDisplay: Boolean
    fun loadSource(url: String)
    fun attachMedia(media: HTMLMediaElement)
    fun destroy()
    companion object { fun isSupported(): Boolean; val Events: HlsEvents }
}
