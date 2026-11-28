package com.trackmygymgains.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class FormatSessionDateTimeTest {

    @Test
    fun formatsItalianDayDateAndTime() {
        // 2026-11-27 is a Friday.
        val millis = Instant.parse("2026-11-27T18:30:00Z").toEpochMilli()

        assertEquals("ven 27 nov · 18:30", formatSessionDateTime(millis, ZoneOffset.UTC, Locale.ITALIAN))
    }

    @Test
    fun usesTheGivenZone() {
        // 23:30 UTC on Friday is 00:30 on Saturday in Rome (UTC+1 in November).
        val millis = Instant.parse("2026-11-27T23:30:00Z").toEpochMilli()

        assertEquals("sab 28 nov · 00:30", formatSessionDateTime(millis, ZoneId.of("Europe/Rome"), Locale.ITALIAN))
    }

    @Test
    fun padsHoursAndMinutesToTwoDigits() {
        val millis = Instant.parse("2026-01-05T07:05:00Z").toEpochMilli()

        assertEquals("lun 5 gen · 07:05", formatSessionDateTime(millis, ZoneOffset.UTC, Locale.ITALIAN))
    }
}
