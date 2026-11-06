package org.viptv.app.preview

import org.viptv.app.connection.calendarClock
import org.viptv.app.features.calendar.*
import org.viptv.app.resources.Res

internal object PreviewCalendar {
    val days = listOf(
        CalendarDay("13", "M", "13", "Mon 13", "October 13"), CalendarDay("14", "T", "14", "Today", "October 14"),
        CalendarDay("15", "W", "15", "Tomorrow", "October 15"), CalendarDay("16", "T", "16", "Thu 16", "October 16"),
        CalendarDay("17", "F", "17", "Friday", "October 17"), CalendarDay("18", "S", "18", "Sat 18", "October 18"),
        CalendarDay("19", "S", "19", "Sun 19", "October 19"))
    private val now = calendarClock().millis
    private fun fromNow(days: Long = 0, hours: Long = 0, minutes: Long = 0) = now + ((days * 24 + hours) * 60 + minutes) * 60_000
    val entries = listOf(
        CalendarEntry("naruto", "14", "6:00 AM", PreviewData.watching[3].copy(title = "Naruto Shippūden"),
            "E501 · [Episode title] · TV Tokyo", true, "Episode 501 aired at 6:00 AM.", airMillis = fromNow(hours = -2)),
        CalendarEntry("ministry", "14", "3:00 AM", PreviewData.posters[2].copy(title = "The Ministry"),
            "S2 E3 · [Episode title] · Prime Video", true, airMillis = fromNow(hours = -3)),
        CalendarEntry("muppet", "14", "9:00 PM", PreviewData.posters[1].copy(title = "The Muppet Show", poster = Res.getUri("files/ui-preview/muppets.jpg")),
            "S1 E3 · [Episode title] · Disney+", airMillis = fromNow(hours = 6, minutes = 40)),
        CalendarEntry("rezero", "15", "11:30 AM", PreviewData.watching[1], "E16 · [Episode title] · Crunchyroll", airMillis = fromNow(hours = 21, minutes = 10)),
        CalendarEntry("magic", "17", "All day", PreviewData.posters[0].copy(title = "Practical Magic 2", poster = Res.getUri("files/ui-preview/practical-magic.jpg")), "Digital · Digital", airMillis = fromNow(days = 3, hours = 2)),
        CalendarEntry("hail-mary", "15", "All day", PreviewData.posters[5], "Digital · Digital", airMillis = fromNow(days = 1, hours = 2)),
    )
}
