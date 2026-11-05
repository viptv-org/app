package org.viptv.app.features.live

import java.time.Instant
import java.time.ZoneId

internal actual fun liveClock(): LiveClock {
    val now = Instant.now().atZone(ZoneId.systemDefault())
    val origin = now.withMinute(now.minute / 30 * 30).withSecond(0).withNano(0)
    return LiveClock(origin.toInstant().toEpochMilli(), now.toInstant().toEpochMilli())
}