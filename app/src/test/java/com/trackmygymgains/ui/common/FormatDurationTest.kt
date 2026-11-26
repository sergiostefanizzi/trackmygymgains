package com.trackmygymgains.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDurationTest {
    private val minute = 60_000L

    @Test
    fun underAMinuteIsLessThanOneMinute() {
        assertEquals("< 1 min", formatDuration(0))
        assertEquals("< 1 min", formatDuration(59_999))
    }

    @Test
    fun underAnHourShowsWholeMinutes() {
        assertEquals("1 min", formatDuration(minute))
        assertEquals("48 min", formatDuration(48 * minute + 30_000))
        assertEquals("59 min", formatDuration(59 * minute + 59_000))
    }

    @Test
    fun anHourOrMoreShowsHoursAndMinutes() {
        assertEquals("1 h 00 min", formatDuration(60 * minute))
        assertEquals("1 h 05 min", formatDuration(65 * minute))
        assertEquals("2 h 05 min", formatDuration(125 * minute + 10_000))
    }
}
