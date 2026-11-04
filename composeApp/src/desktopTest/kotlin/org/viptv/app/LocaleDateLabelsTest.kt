package org.viptv.app

import org.viptv.app.connection.*
import java.time.*
import java.util.Locale
import java.util.TimeZone
import kotlin.test.*

class LocaleDateLabelsTest {
    private fun withContext(locale: Locale, zone: ZoneId, block: () -> Unit) {
        val previousLocale = Locale.getDefault()
        val previousZone = TimeZone.getDefault()
        val previousClock = appDateClock
        Locale.setDefault(locale)
        TimeZone.setDefault(TimeZone.getTimeZone(zone.id))
        appDateClock = Clock.fixed(LocalDate.of(2026, 1, 2).atTime(12, 0).atZone(zone).toInstant(), zone)
        try { block() } finally {
            Locale.setDefault(previousLocale)
            TimeZone.setDefault(previousZone)
            appDateClock = previousClock
        }
    }

    private fun millis(zone: ZoneId, date: LocalDate): Long =
        date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun airDatesStayEnglishAcrossDeviceLocales() {
        for (locale in listOf(Locale.US, Locale.UK, Locale.GERMANY)) withContext(locale, ZoneId.of("America/Los_Angeles")) {
            assertEquals("Jan 1", airDateLabel(0L, "2026-01-01"), locale.toString())
            assertEquals("Jan 1, 2026", airDateWithYearLabel(0L, "2026-01-01"), locale.toString())
        }
    }

    @Test fun calendarNamesStayEnglishAcrossDeviceLocales() {
        for (locale in listOf(Locale.US, Locale.UK, Locale.GERMANY)) withContext(locale, ZoneId.of("America/Los_Angeles")) {
            val days = calendarWindow(0).days
            val today = days.first { it.id == "2026-01-02" }
            assertEquals("Fri", today.weekday, locale.toString())
            assertEquals("Today", today.label, locale.toString())
            assertEquals("Jan 2, 2026", today.fullDate, locale.toString())
            val thursday = days.first { it.id == "2026-01-01" }
            assertEquals("Thu", thursday.weekday, locale.toString())
            assertEquals("Thursday", thursday.label, locale.toString())
            assertEquals("Jan 1, 2026", thursday.fullDate, locale.toString())
        }
    }

    @Test fun historyLabelsStayEnglishAcrossDeviceLocales() {
        val zone = ZoneId.of("America/Los_Angeles")
        for (locale in listOf(Locale.US, Locale.UK, Locale.GERMANY)) withContext(locale, zone) {
            assertEquals("Today", historyDayLabel(millis(zone, LocalDate.of(2026, 1, 2))), locale.toString())
            assertEquals("Yesterday", historyDayLabel(millis(zone, LocalDate.of(2026, 1, 1))), locale.toString())
            assertEquals("Tuesday", historyDayLabel(millis(zone, LocalDate.of(2025, 12, 30))), locale.toString())
            assertEquals("Last week", historyDayLabel(millis(zone, LocalDate.of(2025, 12, 25))), locale.toString())
            assertEquals("Dec 13, 2025", historyDayLabel(millis(zone, LocalDate.of(2025, 12, 13))), locale.toString())
        }
    }
}