package org.viptv.app.format

import java.time.Instant
import java.time.ZoneId

internal actual fun clockTimeOfDay(atMillis: Long, period: Boolean): String {
    val at = Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault())
    return formatTimeOfDay(at.hour, at.minute, is24HourTime(), period)
}
