package org.viptv.app.format

internal expect fun clockTimeOfDay(atMillis: Long, period: Boolean = true): String
