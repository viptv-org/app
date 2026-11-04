package org.viptv.app.connection

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970

private fun dateOnlyDate(value: String): NSDate? {
    val formatter = NSDateFormatter().apply {
        dateFormat = "yyyy-MM-dd"
        timeZone = NSTimeZone.localTimeZone
    }
    return formatter.dateFromString(value)
}

internal actual fun airDateLabel(atMillis: Long, dateOnly: String?): String =
    dateOnly?.takeIf(::isDateOnly)?.let(::dateOnlyDate)?.let { date ->
        NSDateFormatter().apply { dateFormat = "MMM d" }.stringFromDate(date)
    } ?: NSDateFormatter().apply { dateFormat = "MMM d" }
        .stringFromDate(NSDate.dateWithTimeIntervalSince1970(atMillis / 1000.0))

internal actual fun airDateWithYearLabel(atMillis: Long, dateOnly: String?): String =
    dateOnly?.takeIf(::isDateOnly)?.let(::dateOnlyDate)?.let { date ->
        NSDateFormatter().apply { dateFormat = "MMM d, yyyy" }.stringFromDate(date)
    } ?: NSDateFormatter().apply { dateFormat = "MMM d, yyyy" }
        .stringFromDate(NSDate.dateWithTimeIntervalSince1970(atMillis / 1000.0))