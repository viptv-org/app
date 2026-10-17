package org.viptv.app.connection

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

internal actual fun historyDayLabel(atMillis: Long): String {
    val date = NSDate.dateWithTimeIntervalSince1970(atMillis / 1000.0)
    val now = NSDate()
    val formatter = NSDateFormatter()
    // The calendar math (Today/Yesterday/weekday/Last week) mirrors the wasm
    // implementation's day-ordinal comparison.
    val ordinal: (NSDate) -> Long = { value ->
        val f = NSDateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        val day = f.stringFromDate(value)
        // Parse back to UTC ordinal via the same string identity.
        val parts = day.split('-').map { it.toLong() }
        // Approximate ordinal via fixed epoch-day formula (proleptic Gregorian).
        val y = parts[0]; val m = parts[1]; val d = parts[2]
        (1461L * (y + 4800 + (m - 14) / 12) / 4 + 367L * (m - 2 - 12L * ((m - 14) / 12)) / 12 - 146097L * (y + 4800 + (m - 14) / 12) / 400 / 1 + d)
    }
    val age = ordinal(now) - ordinal(date)
    return when {
        age == 0L -> "Today"
        age == 1L -> "Yesterday"
        age in 2..6 -> {
            formatter.dateFormat = "EEEE"
            formatter.stringFromDate(date)
        }
        age in 7..13 -> "Last week"
        else -> {
            formatter.dateFormat = "MMM d, yyyy"
            formatter.stringFromDate(date)
        }
    }
}
