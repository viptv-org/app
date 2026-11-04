package org.viptv.app.format

import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle

internal actual fun is24HourTime(): Boolean {
    val formatter = NSDateFormatter()
    formatter.dateStyle = NSDateFormatterNoStyle
    formatter.timeStyle = NSDateFormatterShortStyle
    return formatter.dateFormat?.contains('H') == true
}
