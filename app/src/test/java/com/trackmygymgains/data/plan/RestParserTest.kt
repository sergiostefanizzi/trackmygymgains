package com.trackmygymgains.data.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RestParserTest {
    private fun ok(raw: String?, seconds: Int?) {
        val r = RestParser.parseSeconds(raw)
        assertEquals("seconds for <$raw>", seconds, r.seconds)
        assertNull("no warning for <$raw>", r.warning)
    }

    @Test fun minutes() {
        ok("3'", 180); ok("2'", 120); ok(" 1' ", 60); ok("3’", 180); ok("3′", 180); ok("3‘", 180)
    }

    @Test fun seconds() {
        ok("30\"", 30); ok("30”", 30); ok("30″", 30); ok("30“", 30); ok("30''", 30); ok("45 \"", 45)
    }

    @Test fun minutesAndSeconds() {
        ok("1'30\"", 90); ok("1' 30\"", 90); ok("1’30”", 90); ok("2'15", 135); ok("1'30''", 90)
    }

    @Test fun clock() {
        ok("1:30", 90); ok("0:45", 45); ok("2:00", 120)
    }

    @Test fun words() {
        ok("2 min", 120); ok("2min", 120); ok("90 s", 90); ok("90sec", 90); ok("90 sec", 90); ok("2 MIN", 120)
    }

    @Test fun emptyMeansNoRestWithoutWarning() {
        ok(null, null); ok("", null); ok("   ", null); ok("/", null); ok(" / ", null); ok("-", null)
    }

    @Test fun explicitZero() {
        ok("0", 0); ok("0'", 0); ok("0\"", 0)
    }

    @Test fun bareSmallNumberIsMinutesWithWarning() {
        for ((raw, secs) in listOf("3" to 180, "10" to 600, "3.0" to 180, "2,0" to 120)) {
            val r = RestParser.parseSeconds(raw)
            assertEquals(raw, secs, r.seconds)
            assertNotNull(raw, r.warning)
        }
    }

    @Test fun bareLargeNumberIsSecondsWithWarning() {
        for ((raw, secs) in listOf("11" to 11, "90" to 90, "120.0" to 120)) {
            val r = RestParser.parseSeconds(raw)
            assertEquals(raw, secs, r.seconds)
            assertNotNull(raw, r.warning)
        }
    }

    @Test fun unrecognisedGivesNullWithWarning() {
        for (raw in listOf("abc", "3 volte", "1:75", "-5", "3'x", "'")) {
            val r = RestParser.parseSeconds(raw)
            assertNull(raw, r.seconds)
            assertNotNull(raw, r.warning)
        }
    }
}
