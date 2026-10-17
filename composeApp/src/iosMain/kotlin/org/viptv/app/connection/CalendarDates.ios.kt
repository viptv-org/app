package org.viptv.app.connection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*
import org.viptv.app.features.calendar.CalendarDay
import platform.Foundation.*
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitWeekday

private fun dayString(date: NSDate): String {
    val f = NSDateFormatter()
    f.dateFormat = "yyyy-MM-dd"
    return f.stringFromDate(date)
}

private fun shifted(days: Int): NSDate {
    val calendar = NSCalendar.currentCalendar
    val components = NSDateComponents()
    components.day = days.toLong()
    return calendar.dateByAddingComponents(components, toDate = NSDate(), options = 0u)!!
}

private fun dateFromDayString(value: String): NSDate? {
    val f = NSDateFormatter()
    f.dateFormat = "yyyy-MM-dd"
    f.timeZone = NSTimeZone.localTimeZone
    return f.dateFromString(value)
}

internal actual fun calendarWindow(offsetDays: Int): CalendarWindow {
    val now = NSDate()
    val todayId = dayString(now)
    val shortWeekday = NSDateFormatter().apply { dateFormat = "EEE" }
    val longWeekday = NSDateFormatter().apply { dateFormat = "EEEE" }
    val fullDate = NSDateFormatter().apply { dateFormat = "MMM d, yyyy" }
    val days = (-3..3).map { n ->
        val d = shifted(offsetDays + n)
        val id = dayString(d)
        CalendarDay(
            id = id,
            weekday = shortWeekday.stringFromDate(d),
            number = NSCalendar.currentCalendar.components(NSCalendarUnitDay, fromDate = d).day.toString(),
            label = if (id == todayId) "Today" else longWeekday.stringFromDate(d),
            fullDate = fullDate.stringFromDate(d),
        )
    }
    return CalendarWindow(
        start = days.first().id,
        end = days.last().id,
        days = days,
    )
}

internal actual fun calendarLabel(date: String): CalendarLabel? {
    val d = dateFromDayString(date) ?: return null
    val time = NSDateFormatter().apply { dateFormat = "h:mm a" }
    return CalendarLabel(
        day = dayString(d),
        time = time.stringFromDate(d),
        aired = d.timeIntervalSince1970 < NSDate().timeIntervalSince1970,
    )
}
