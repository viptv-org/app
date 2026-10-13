package org.viptv.app.connection

import java.time.*
import java.time.format.DateTimeFormatter
import org.viptv.app.features.calendar.CalendarDay

internal actual fun calendarWindow(): CalendarWindow {
    val today = LocalDate.now()
    val days = (-3L..3L).map { offset ->
        val day = today.plusDays(offset)
        CalendarDay(day.toString(), day.format(DateTimeFormatter.ofPattern("EEE")), day.dayOfMonth.toString(),
            if (offset == 0L) "Today" else day.format(DateTimeFormatter.ofPattern("EEEE")), day.format(DateTimeFormatter.ofPattern("MMM d, yyyy")))
    }
    return CalendarWindow(today.minusDays(4).toString(), today.plusDays(4).toString(), days)
}
internal actual fun calendarLabel(date: String): CalendarLabel? = runCatching {
    val instant = Instant.parse(date)
    val local = instant.atZone(ZoneId.systemDefault())
    CalendarLabel(local.toLocalDate().toString(), local.format(DateTimeFormatter.ofPattern("h:mm a")), instant.isBefore(Instant.now()))
}.getOrNull()
