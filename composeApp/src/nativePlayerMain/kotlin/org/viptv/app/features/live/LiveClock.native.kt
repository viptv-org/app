package org.viptv.app.features.live

import org.viptv.app.format.formatTimeOfDay
import org.viptv.app.format.is24HourTime
import java.time.Instant
import java.time.ZoneId

internal actual fun liveClock(): LiveClock {
    val now = Instant.now().atZone(ZoneId.systemDefault())
    val origin = now.withMinute(now.minute / 30 * 30).withSecond(0).withNano(0)
    return LiveClock(origin.toInstant().toEpochMilli(), now.toInstant().toEpochMilli())
}
internal actual fun liveTimeAt(atMillis: Long, period: Boolean): String {
    val at = Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault())
    return formatTimeOfDay(at.hour, at.minute, is24HourTime(), period)
}
