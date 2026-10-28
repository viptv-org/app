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
    val days = (-3L..3L).map { offset ->
        val day = center.plusDays(offset)
        CalendarDay(day.toString(), day.format(DateTimeFormatter.ofPattern("EEE")), day.dayOfMonth.toString(),
            if (day == today) "Today" else day.format(DateTimeFormatter.ofPattern("EEEE")), day.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
    }
    return CalendarWindow(center.minusDays(4).toString(), center.plusDays(4).toString(), days)
}
internal actual fun calendarLabel(date: String): CalendarLabel? = runCatching {
    val instant = Instant.parse(date)
    val clock = clock()
    val local = instant.atZone(clock.zone)
    CalendarLabel(local.toLocalDate().toString(), local.format(DateTimeFormatter.ofPattern("h:mm a")), instant.isBefore(clock.instant()))
}.getOrNull()
