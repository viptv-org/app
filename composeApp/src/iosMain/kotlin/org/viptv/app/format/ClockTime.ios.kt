package org.viptv.app.format

import platform.Foundation.*

internal actual fun clockTimeOfDay(atMillis: Long, period: Boolean): String {
    val date = NSDate.dateWithTimeIntervalSince1970(atMillis / 1000.0)
    val components = NSCalendar.currentCalendar.components(NSCalendarUnitHour or NSCalendarUnitMinute, fromDate = date)
    return formatTimeOfDay(components.hour.toInt(), components.minute.toInt(), is24HourTime(), period)
}
