package org.viptv.app.connection

internal expect fun airDateLabel(atMillis: Long, dateOnly: String? = null): String
internal expect fun airDateWithYearLabel(atMillis: Long, dateOnly: String? = null): String

internal fun isDateOnly(value: String): Boolean =
    value.length == 10 && value[4] == '-' && value[7] == '-'