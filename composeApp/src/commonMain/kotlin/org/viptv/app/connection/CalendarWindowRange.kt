package org.viptv.app.connection

internal const val CALENDAR_VISIBLE_DAYS_PER_SIDE = 3
internal const val CALENDAR_REQUEST_MARGIN_DAYS = 1

internal data class CalendarRequestRange(val start: String, val end: String)

internal fun calendarRequestRange(center: String): CalendarRequestRange {
    val ordinal = dayOrdinal(center)
    val margin = CALENDAR_VISIBLE_DAYS_PER_SIDE + CALENDAR_REQUEST_MARGIN_DAYS
    return CalendarRequestRange(dayString(ordinal - margin), dayString(ordinal + margin))
}

private fun dayOrdinal(day: String): Long {
    val parts = day.split('-')
    val year = parts[0].toLong()
    val month = parts[1].toLong()
    val dayOfMonth = parts[2].toLong()
    val shiftedYear = year - if (month <= 2) 1 else 0
    val era = (if (shiftedYear >= 0) shiftedYear else shiftedYear - 399) / 400
    val yearOfEra = shiftedYear - era * 400
    val dayOfYear = (153 * (month + if (month > 2) -3 else 9) + 2) / 5 + dayOfMonth - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era * 146097 + dayOfEra - 719468
}

private fun dayString(ordinal: Long): String {
    val shifted = ordinal + 719468
    val era = (if (shifted >= 0) shifted else shifted - 146096) / 146097
    val dayOfEra = shifted - era * 146097
    val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
    val year = yearOfEra + era * 400
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthPart = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * monthPart + 2) / 5 + 1
    val month = monthPart + if (monthPart < 10) 3 else -9
    val resultYear = year + if (month <= 2) 1 else 0
    return "${resultYear.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}