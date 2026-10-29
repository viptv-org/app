package org.viptv.app.connection

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

internal actual fun airDateLabel(atMillis: Long): String =
    NSDateFormatter().apply { dateFormat = "MMM d" }
        .stringFromDate(NSDate.dateWithTimeIntervalSince1970(atMillis / 1000.0))