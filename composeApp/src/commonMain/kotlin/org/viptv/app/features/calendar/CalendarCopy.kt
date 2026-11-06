package org.viptv.app.features.calendar

// copy.md §Empty states — the Calendar.
internal const val CALENDAR_EMPTY_WEEK = "Nothing is scheduled in these dates."
internal const val CALENDAR_UNAVAILABLE = "The calendar needs series in My List or a Simkl connection."
internal const val CALENDAR_EMPTY_DAY_YOUR_SHOWS = "None of your shows air this day."
internal const val CALENDAR_EMPTY_DAY_ALL = "No releases for this day."
internal const val CALENDAR_FAILED_TITLE = "Couldn’t load the calendar"
internal const val CALENDAR_MANAGE = "Manage integrations"
internal const val CALENDAR_RETRY = "Try again"

internal fun calendarEmptyDay(filter: CalendarFilter?) =
    if (filter == CalendarFilter.YourShows) CALENDAR_EMPTY_DAY_YOUR_SHOWS else CALENDAR_EMPTY_DAY_ALL