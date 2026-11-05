@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.format

internal actual fun clockTimeOfDay(atMillis: Long, period: Boolean): String {
    val minuteOfDay = minuteOfDay(atMillis.toDouble())
    return formatTimeOfDay(minuteOfDay / 60, minuteOfDay % 60, is24HourTime(), period)
}

@JsFun("""(value) => { const date = new Date(value); return date.getHours() * 60 + date.getMinutes(); }""")
private external fun minuteOfDay(value: Double): Int
