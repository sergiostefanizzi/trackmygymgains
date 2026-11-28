package com.trackmygymgains

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppContextTest {
    @Test
    fun packageNameMatches() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.trackmygymgains", ctx.packageName)
    }
}
