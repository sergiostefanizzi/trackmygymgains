package com.trackmygymgains

import org.junit.Assert.assertEquals
import org.junit.Test

class WeightFormatTest {
    @Test
    fun wholeNumbersHaveNoDecimals() = assertEquals("80", formatWeightKg(80.0))

    @Test
    fun fractionsAreKept() = assertEquals("82.5", formatWeightKg(82.5))
}
