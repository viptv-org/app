package org.viptv.app.features.calendar

import org.viptv.engine.wire.Card

data class CalendarDay(val id: String, val weekday: String, val number: String, val label: String, val fullDate: String)
data class CalendarEntry(val id: String, val day: String, val time: String, val card: Card,
    val subtitle: String? = null, val status: String? = null, val aired: Boolean = false, val description: String? = null)
