package org.viptv.app.player

import org.viptv.app.format.formatTimeOfDay
import org.viptv.app.format.is24HourTime
import java.time.ZonedDateTime

internal actual fun playbackEndTime(remainingMillis: Long): String {
    val ends = ZonedDateTime.now().plusSeconds(remainingMillis.coerceAtLeast(0) / 1000)
    return formatTimeOfDay(ends.hour, ends.minute, is24HourTime())
}
