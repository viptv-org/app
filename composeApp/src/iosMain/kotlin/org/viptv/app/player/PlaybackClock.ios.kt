package org.viptv.app.player

import platform.Foundation.*

internal actual fun playbackEndTime(remainingMillis: Long): String {
    val ends = NSDate.dateWithTimeIntervalSince1970(NSDate().timeIntervalSince1970 + remainingMillis.coerceAtLeast(0) / 1000.0)
    val f = NSDateFormatter().apply { dateFormat = "h:mm a" }
    return f.stringFromDate(ends)
}
