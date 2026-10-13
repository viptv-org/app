@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.features.live

internal actual fun liveClock(): LiveClock = LiveClock(windowOrigin().toLong(), nowMillis().toLong())
internal actual fun liveTimeAt(atMillis: Long, period: Boolean): String = formatLiveTime(atMillis.toDouble(), period)

@JsFun("""() => Date.now()""")
private external fun nowMillis(): Double
@JsFun("""() => { const value = new Date(); value.setMinutes(Math.floor(value.getMinutes()/30)*30, 0, 0); return value.getTime(); }""")
private external fun windowOrigin(): Double
@JsFun("""(value, period) => { const date = new Date(value), hour = date.getHours(); return ((hour+11)%12+1)+':'+String(date.getMinutes()).padStart(2,'0')+(period ? (hour<12 ? ' AM' : ' PM') : ''); }""")
private external fun formatLiveTime(value: Double, period: Boolean): String
