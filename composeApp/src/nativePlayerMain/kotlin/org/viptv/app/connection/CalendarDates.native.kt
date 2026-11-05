package org.viptv.app.connection

import java.time.*
import java.time.format.DateTimeFormatter
import org.viptv.app.features.calendar.CalendarDay
import org.viptv.app.format.formatTimeOfDay
import org.viptv.app.format.is24HourTime

internal actual fun calendarWindow(offsetDays: Int): CalendarWindow {
    val today = LocalDate.now(clock())
    val center = today.plusDays(offsetDays.toLong())
    val days = (-CALENDAR_VISIBLE_DAYS_PER_SIDE.toLong()..CALENDAR_VISIBLE_DAYS_PER_SIDE.toLong()).map { offset ->
        val day = center.plusDays(offset)
        CalendarDay(day.toString(), day.format(DateTimeFormatter.ofPattern("EEE", appDateLocale)), day.dayOfMonth.toString(),
            if (day == today) "Today" else day.format(DateTimeFormatter.ofPattern("EEEE", appDateLocale)), day.format(DateTimeFormatter.ofPattern("MMM d, yyyy", appDateLocale)))
    }
    val range = calendarRequestRange(center.toString())
    return CalendarWindow(range.start, range.end, days)
}
internal actual fun calendarLabel(date: String): CalendarLabel? = runCatching {
    if (isDateOnly(date)) {
        val day = LocalDate.parse(date)
        val today = LocalDate.now(clock())
        return@runCatching CalendarLabel(day.toString(), "All day", !day.isAfter(today))
    }
    val instant = Instant.parse(date)
    val clock = clock()
    val local = instant.atZone(clock.zone)
    CalendarLabel(local.toLocalDate().toString(), formatTimeOfDay(local.hour, local.minute, is24HourTime()), instant.isBefore(clock.instant()))
}.getOrNull()
