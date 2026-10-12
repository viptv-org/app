package org.viptv.app.preview

import org.viptv.app.features.calendar.*
import org.viptv.app.resources.Res
import org.viptv.engine.wire.MediaIdentity

internal object PreviewTvCalendar {
    val days = PreviewCalendar.days.filter { it.id in listOf("14", "15", "17", "19") }.map {
        if (it.id == "17") it.copy(label = "Fri 17") else it
    } + listOf(CalendarDay("21", "T", "21", "Tue 21", "October 21"), CalendarDay("22", "W", "22", "Wed 22", "October 22"))
    val entries = PreviewCalendar.entries.map { entry -> when (entry.id) {
        "naruto" -> entry.copy(card = entry.card.copy(title = "Naruto Shippûden", identity = MediaIdentity("tv-calendar-naruto", "series", emptyList()),
            poster = Res.getUri("files/ui-preview/history4.jpg"), landscape = Res.getUri("files/ui-preview/history4.jpg")), status = "Aired",
            description = "Episode 501 aired at 6:00 AM. It’s on your Watching list — 480 of 500 watched.")
        "ministry" -> entry.copy(status = "Aired")
        else -> entry
    } }
    val actions = mapOf("naruto" to TvCalendarAction("Watch E481", entries.first().card.copy(
        identity = MediaIdentity("tv-calendar-naruto-481", "episode", emptyList())), "Data from Simkl"))
}
