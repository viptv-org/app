package org.viptv.app.player

import org.w3c.dom.HTMLMediaElement

@JsModule("hls.js")
external class Hls : JsAny {
    constructor()
    fun loadSource(url: String)
    fun attachMedia(media: HTMLMediaElement)
    fun destroy()
    companion object { fun isSupported(): Boolean }
}
