@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.features.live

import org.viptv.app.format.formatTimeOfDay
import org.viptv.app.format.is24HourTime

internal actual fun liveClock(): LiveClock = LiveClock(windowOrigin().toLong(), nowMillis().toLong())
internal actual fun liveTimeAt(atMillis: Long, period: Boolean): String {
    val minuteOfDay = minuteOfDay(atMillis.toDouble())
    return formatTimeOfDay(minuteOfDay / 60, minuteOfDay % 60, is24HourTime(), period)
}

@JsFun("""() => Date.now()""")
private external fun nowMillis(): Double
@JsFun("""() => { const value = new Date(); value.setMinutes(Math.floor(value.getMinutes()/30)*30, 0, 0); return value.getTime(); }""")
private external fun windowOrigin(): Double
@JsFun("""(value) => { const date = new Date(value); return date.getHours() * 60 + date.getMinutes(); }""")
private external fun minuteOfDay(value: Double): Int
