package org.viptv.app.connection

import java.time.*
import java.time.format.DateTimeFormatter
import org.viptv.app.features.calendar.CalendarDay

/**
 * The clock that decides the calendar's "today" and which releases have aired. Tests fix
 * it, so their fixtures do not depend on the date or time of day they run at (app#106).
 * Null is the system clock in the current default zone, read on every use.
 */
@Volatile internal var calendarClock: Clock? = null
private fun clock(): Clock = calendarClock ?: Clock.systemDefaultZone()

internal actual fun calendarWindow(offsetDays: Int): CalendarWindow {
    val today = LocalDate.now(clock())
    val center = today.plusDays(offsetDays.toLong())
    val days = (-CALENDAR_VISIBLE_DAYS_PER_SIDE.toLong()..CALENDAR_VISIBLE_DAYS_PER_SIDE.toLong()).map { offset ->
        val day = center.plusDays(offset)
        CalendarDay(day.toString(), day.format(DateTimeFormatter.ofPattern("EEE")), day.dayOfMonth.toString(),
            if (day == today) "Today" else day.format(DateTimeFormatter.ofPattern("EEEE")), day.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
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
    CalendarLabel(local.toLocalDate().toString(), local.format(DateTimeFormatter.ofPattern("h:mm a")), instant.isBefore(clock.instant()))
}.getOrNull()
