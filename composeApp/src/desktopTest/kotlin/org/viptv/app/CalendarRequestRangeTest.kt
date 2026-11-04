package org.viptv.app

import org.viptv.app.connection.calendarRequestRange
import kotlin.test.*

class CalendarRequestRangeTest {
    @Test fun theRangeAddsAUtcMarginAroundTheSevenVisibleDays() {
        assertEquals("2026-10-10" to "2026-10-18", range("2026-10-14"))
    }

    @Test fun theRangeCrossesMonthAndYearBoundaries() {
        assertEquals("2026-03-09" to "2026-03-17", range("2026-03-13"))
        assertEquals("2025-12-28" to "2026-01-05", range("2026-01-01"))
        assertEquals("2026-02-24" to "2026-03-04", range("2026-02-28"))
        assertEquals("2024-02-25" to "2024-03-04", range("2024-02-29"))
    }

    private fun range(center: String) = calendarRequestRange(center).let { it.start to it.end }
}