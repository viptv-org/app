package org.viptv.app.player

import org.viptv.app.format.formatTimeOfDay
import org.viptv.app.format.is24HourTime
import platform.Foundation.*

internal actual fun playbackEndTime(remainingMillis: Long): String {
    val ends = NSDate.dateWithTimeIntervalSince1970(NSDate().timeIntervalSince1970 + remainingMillis.coerceAtLeast(0) / 1000.0)
    val components = NSCalendar.currentCalendar.components(NSCalendarUnitHour or NSCalendarUnitMinute, fromDate = ends)
    return formatTimeOfDay(components.hour.toInt(), components.minute.toInt(), is24HourTime())
}
