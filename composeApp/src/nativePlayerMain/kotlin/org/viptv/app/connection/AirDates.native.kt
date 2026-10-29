package org.viptv.app.connection

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal actual fun airDateLabel(atMillis: Long): String =
    Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofPattern("MMM d"))