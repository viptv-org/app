@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.features.live

internal actual fun liveClock(): LiveClock = LiveClock(windowOrigin().toLong(), nowMillis().toLong())

@JsFun("""() => Date.now()""")
private external fun nowMillis(): Double
@JsFun("""() => { const value = new Date(); value.setMinutes(Math.floor(value.getMinutes()/30)*30, 0, 0); return value.getTime(); }""")
private external fun windowOrigin(): Double
