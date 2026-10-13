package org.viptv.app.features.live

import androidx.compose.runtime.staticCompositionLocalOf

data class LiveClock(val originMillis: Long, val nowMillis: Long) {
    fun minute(atMillis: Long) = ((atMillis - originMillis) / 60_000).toInt()
    val nowMinute get() = minute(nowMillis)
}
internal val LocalLiveClock = staticCompositionLocalOf<LiveClock?> { null }
internal expect fun liveClock(): LiveClock
internal expect fun liveTimeAt(atMillis: Long, period: Boolean): String
