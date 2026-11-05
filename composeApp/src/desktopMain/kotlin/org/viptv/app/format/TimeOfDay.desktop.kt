package org.viptv.app.format

import java.text.DateFormat
import java.text.SimpleDateFormat

internal actual fun is24HourTime(): Boolean =
    (DateFormat.getTimeInstance(DateFormat.SHORT) as? SimpleDateFormat)?.toPattern()?.any { it == 'H' || it == 'k' } ?: false
