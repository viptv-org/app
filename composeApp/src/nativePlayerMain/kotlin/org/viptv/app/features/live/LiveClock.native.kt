package org.viptv.app.features.live

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal actual fun liveClock(): LiveClock {
    val now = Instant.now().atZone(ZoneId.systemDefault())
    val origin = now.withMinute(now.minute / 30 * 30).withSecond(0).withNano(0)
    return LiveClock(origin.toInstant().toEpochMilli(), now.toInstant().toEpochMilli())
}
internal actual fun liveTimeAt(atMillis: Long, period: Boolean): String =
    Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(if (period) "h:mm a" else "h:mm", Locale.US))
