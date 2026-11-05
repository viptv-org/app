@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package org.viptv.app.player

import org.viptv.app.format.formatTimeOfDay
import org.viptv.app.format.is24HourTime

@JsFun("""(ms) => { const date = new Date(Date.now() + ms); return date.getHours() * 60 + date.getMinutes(); }""")
private external fun endMinuteOfDay(ms: Double): Int

internal actual fun playbackEndTime(remainingMillis: Long): String {
    val minuteOfDay = endMinuteOfDay(remainingMillis.coerceAtLeast(0).toDouble())
    return formatTimeOfDay(minuteOfDay / 60, minuteOfDay % 60, is24HourTime())
}
