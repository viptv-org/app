package org.viptv.app.features.live

import platform.Foundation.*

internal actual fun liveClock(): LiveClock {
    val now = NSDate()
    val calendar = NSCalendar.currentCalendar
    val flags = NSCalendarUnitHour or NSCalendarUnitMinute
    val components = calendar.components(flags, fromDate = now)
    // Snap to the last 30-minute boundary, matching the wasm window origin.
    components.minute = (components.minute / 30) * 30
    components.second = 0
    val origin = calendar.dateFromComponents(components)!!
    return LiveClock(origin.timeIntervalSince1970.toLong() * 1000L,
        (now.timeIntervalSince1970.toLong()) * 1000L)
}

internal actual fun liveTimeAt(atMillis: Long, period: Boolean): String {
    val date = NSDate.dateWithTimeIntervalSince1970(atMillis / 1000.0)
    val hourFormat = NSDateFormatter().apply { dateFormat = "h:mm" }
    val time = hourFormat.stringFromDate(date)
    if (!period) return time
    val amPm = NSDateFormatter().apply { dateFormat = "a" }
    return "$time ${amPm.stringFromDate(date)}"
}
