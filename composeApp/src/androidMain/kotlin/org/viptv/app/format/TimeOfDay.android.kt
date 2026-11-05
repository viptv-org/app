package org.viptv.app.format

import android.content.Context
import android.text.format.DateFormat

object AndroidTimeOfDay {
    @Volatile internal var context: Context? = null
    fun attach(context: Context) { this.context = context.applicationContext }
}

internal actual fun is24HourTime(): Boolean =
    AndroidTimeOfDay.context?.let { DateFormat.is24HourFormat(it) } ?: false
