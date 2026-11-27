package com.trackmygymgains.timer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesRestTimerStoreTest {
    private fun store() = SharedPreferencesRestTimerStore(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun roundTrip() {
        val s = store()
        assertNull(s.load())
        val saved = SavedRestTimer(123_456L, 90, "Squat")
        s.save(saved)
        assertEquals(saved, store().load())
        s.clear()
        assertNull(s.load())
    }

    @Test
    fun nullLabel() {
        val s = store()
        s.save(SavedRestTimer(1L, 30, "x"))
        s.save(SavedRestTimer(2L, 45, null))
        assertEquals(SavedRestTimer(2L, 45, null), s.load())
    }
}
