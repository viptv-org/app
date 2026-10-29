package org.viptv.app.connection

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** `Nov 4`: the month and day the announced episode airs, never the year (app#132). */
internal actual fun airDateLabel(atMillis: Long): String =
    Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("MMM d"))