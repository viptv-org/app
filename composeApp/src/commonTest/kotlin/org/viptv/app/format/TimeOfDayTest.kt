package org.viptv.app.format

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeOfDayTest {
    @Test
    fun midnight() {
        assertEquals("00:00", formatTimeOfDay(0, 0, is24Hour = true))
        assertEquals("12:00 AM", formatTimeOfDay(0, 0, is24Hour = false))
    }

    @Test
    fun pastMidnight() {
        assertEquals("00:05", formatTimeOfDay(0, 5, is24Hour = true))
        assertEquals("12:05 AM", formatTimeOfDay(0, 5, is24Hour = false))
    }

    @Test
    fun noon() {
        assertEquals("12:00", formatTimeOfDay(12, 0, is24Hour = true))
        assertEquals("12:00 PM", formatTimeOfDay(12, 0, is24Hour = false))
    }

    @Test
    fun afternoon() {
        assertEquals("13:05", formatTimeOfDay(13, 5, is24Hour = true))
        assertEquals("1:05 PM", formatTimeOfDay(13, 5, is24Hour = false))
    }

    @Test
    fun minuteIsPadded() {
        assertEquals("09:07", formatTimeOfDay(9, 7, is24Hour = true))
        assertEquals("9:07 AM", formatTimeOfDay(9, 7, is24Hour = false))
    }

    @Test
    fun suppressedPeriodDropsAmPm() {
        assertEquals("12:30", formatTimeOfDay(12, 30, is24Hour = false, period = false))
        assertEquals("00:30", formatTimeOfDay(0, 30, is24Hour = true, period = false))
    }
}
