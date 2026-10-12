package org.viptv.app.player

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val endFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
internal actual fun playbackEndTime(remainingMillis: Long): String = ZonedDateTime.now()
    .plusSeconds(remainingMillis.coerceAtLeast(0) / 1000).format(endFormat)
