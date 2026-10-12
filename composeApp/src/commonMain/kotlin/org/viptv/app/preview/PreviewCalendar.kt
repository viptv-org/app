package org.viptv.app.preview

import org.viptv.app.features.calendar.*
import org.viptv.app.resources.Res

internal object PreviewCalendar {
    val days = listOf(
        CalendarDay("13", "M", "13", "Mon 13", "October 13"), CalendarDay("14", "T", "14", "Today", "October 14"),
        CalendarDay("15", "W", "15", "Tomorrow", "October 15"), CalendarDay("16", "T", "16", "Thu 16", "October 16"),
        CalendarDay("17", "F", "17", "Friday", "October 17"), CalendarDay("18", "S", "18", "Sat 18", "October 18"),
        CalendarDay("19", "S", "19", "Sun 19", "October 19"))
    val entries = listOf(
        CalendarEntry("naruto", "14", "6:00 AM", PreviewData.watching[3].copy(title = "Naruto Shippūden"),
            "E501 · [Episode title] · TV Tokyo", "Out now", true, "Episode 501 aired at 6:00 AM."),
        CalendarEntry("ministry", "14", "3:00 AM", PreviewData.posters[2].copy(title = "The Ministry"),
            "S2 E3 · [Episode title] · Prime Video", "Out now", true),
        CalendarEntry("muppet", "14", "9:00 PM", PreviewData.posters[1].copy(title = "The Muppet Show", poster = Res.getUri("files/ui-preview/muppets.jpg")),
            "S1 E3 · [Episode title] · Disney+", "in 6h 40m"),
        CalendarEntry("rezero", "15", "11:30 AM", PreviewData.watching[1], "E16 · [Episode title] · Crunchyroll", "in 21h 10m"),
        CalendarEntry("magic", "17", "All day", PreviewData.posters[0].copy(title = "Practical Magic 2", poster = Res.getUri("files/ui-preview/practical-magic.jpg")), "Digital · Digital", "in 3d 2h"),
        CalendarEntry("hail-mary", "15", "All day", PreviewData.posters[5], "Digital · Digital", "in 1d 2h"),
    )
}
