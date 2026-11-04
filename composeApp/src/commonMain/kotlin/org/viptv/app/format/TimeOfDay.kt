package org.viptv.app.format

internal expect fun is24HourTime(): Boolean

internal fun formatTimeOfDay(hour: Int, minute: Int, is24Hour: Boolean, period: Boolean = true): String {
    val padded = minute.toString().padStart(2, '0')
    if (is24Hour) return "${hour.toString().padStart(2, '0')}:$padded"
    return "${(hour + 11) % 12 + 1}:$padded" + if (period) { if (hour < 12) " AM" else " PM" } else ""
}
