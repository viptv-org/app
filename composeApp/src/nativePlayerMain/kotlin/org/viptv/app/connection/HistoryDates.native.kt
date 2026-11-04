package org.viptv.app.connection

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal actual fun historyDayLabel(atMillis: Long): String {
    val clock = clock()
    val day = Instant.ofEpochMilli(atMillis).atZone(clock.zone).toLocalDate()
    return when (ChronoUnit.DAYS.between(day, LocalDate.now(clock))) {
        0L -> "Today"
        1L -> "Yesterday"
        in 2L..6L -> day.format(DateTimeFormatter.ofPattern("EEEE", appDateLocale))
        in 7L..13L -> "Last week"
        else -> day.format(DateTimeFormatter.ofPattern("MMM d, yyyy", appDateLocale))
    }
}
