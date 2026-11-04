package org.viptv.app.connection

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun nativeDay(atMillis: Long, dateOnly: String?): LocalDate =
    dateOnly?.takeIf(::isDateOnly)?.let(LocalDate::parse)
        ?: Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).toLocalDate()

internal actual fun airDateLabel(atMillis: Long, dateOnly: String?): String =
    nativeDay(atMillis, dateOnly).format(DateTimeFormatter.ofPattern("MMM d"))

internal actual fun airDateWithYearLabel(atMillis: Long, dateOnly: String?): String =
    nativeDay(atMillis, dateOnly).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))