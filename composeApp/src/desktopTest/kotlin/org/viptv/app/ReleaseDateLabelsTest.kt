package org.viptv.app

import org.viptv.app.connection.*
import java.time.*
import java.util.TimeZone
import kotlin.test.*

class ReleaseDateLabelsTest {
    private fun withZone(zone: String, block: () -> Unit) {
        val previousZone = TimeZone.getDefault()
        val previousClock = calendarClock
        val now = ZonedDateTime.of(LocalDate.of(2026, 1, 2), LocalTime.of(12, 0), ZoneId.of(zone))
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        calendarClock = Clock.fixed(now.toInstant(), now.zone)
        try { block() } finally { calendarClock = previousClock; TimeZone.setDefault(previousZone) }
    }

    @Test
    fun aDateOnlyReleaseStaysOnItsDateInEveryZone() {
        for (zone in listOf("America/Los_Angeles", "Asia/Tokyo")) withZone(zone) {
            val label = requireNotNull(calendarLabel("2026-01-01"))
            assertEquals("2026-01-01", label.day, zone)
            assertEquals("All day", label.time, zone)
            assertEquals("Jan 1", airDateLabel(0L, "2026-01-01"), zone)
            assertEquals("Jan 1, 2026", airDateWithYearLabel(0L, "2026-01-01"), zone)
        }
    }

    @Test
    fun aTimedReleaseStillLocalizes() {
        val instant = Instant.parse("2026-01-01T04:00:00Z")
        withZone("America/Los_Angeles") {
            assertEquals("2025-12-31", requireNotNull(calendarLabel("2026-01-01T04:00:00Z")).day)
            assertEquals("Dec 31, 2025", airDateWithYearLabel(instant.toEpochMilli()))
        }
        withZone("Asia/Tokyo") {
            assertEquals("2026-01-01", requireNotNull(calendarLabel("2026-01-01T04:00:00Z")).day)
            assertEquals("Jan 1, 2026", airDateWithYearLabel(instant.toEpochMilli()))
        }
    }
}