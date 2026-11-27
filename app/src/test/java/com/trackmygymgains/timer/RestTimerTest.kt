package com.trackmygymgains.timer

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Clock tied to the test scheduler's virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
private class SchedulerClock(private val scheduler: TestCoroutineScheduler, private val base: Long = 1_000_000L) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(millis())
    override fun millis(): Long = base + scheduler.currentTime
}

@OptIn(ExperimentalCoroutinesApi::class)
class RestTimerTest {
    private class Env(val scope: TestScope) {
        val clock = SchedulerClock(scope.testScheduler)
        val store = InMemoryRestTimerStore()
        fun timer() = RestTimer(scope.backgroundScope, store, clock)
    }

    private fun TestScope.env() = Env(this)

    private fun RestTimer.running() = state.value as RestTimerState.Running

    @Test
    fun start_runningWithFullTime() = runTest {
        val e = env(); val t = e.timer()
        t.start(180, "Squat")
        val s = t.running()
        assertEquals(180, s.remainingSeconds)
        assertEquals(180, s.totalSeconds)
        assertEquals("Squat", s.label)
        assertEquals(e.clock.millis() + 180_000, s.endAtMillis)
        assertEquals(180, e.store.saved?.totalSeconds)
    }

    @Test
    fun timeAdvance_reducesRemaining() = runTest {
        val e = env(); val t = e.timer()
        t.start(60)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(50, t.running().remainingSeconds)
        advanceTimeBy(49_500); runCurrent()
        assertEquals(1, t.running().remainingSeconds)
    }

    @Test
    fun reachingEnd_finishesExactlyOnce() = runTest {
        val e = env(); val t = e.timer()
        val seen = mutableListOf<RestTimerState>()
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) { t.state.collect { seen.add(it) } }
        t.start(5, "x")
        advanceTimeBy(5_000); runCurrent()
        advanceTimeBy(30_000); runCurrent()
        assertEquals(1, seen.count { it is RestTimerState.Finished })
        assertEquals(RestTimerState.Finished(5, "x", restored = false), t.state.value)
        assertNull(e.store.saved)
        t.acknowledgeFinished()
        assertEquals(RestTimerState.Idle, t.state.value)
    }

    @Test
    fun skip_idleAndStoreCleared() = runTest {
        val e = env(); val t = e.timer()
        t.start(30)
        t.skip()
        assertEquals(RestTimerState.Idle, t.state.value)
        assertNull(e.store.saved)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(RestTimerState.Idle, t.state.value)
    }

    @Test
    fun adjust_plusAndMinus() = runTest {
        val e = env(); val t = e.timer()
        t.start(60)
        t.adjust(15)
        assertEquals(75, t.running().remainingSeconds)
        assertEquals(75, t.running().totalSeconds) // exceeds the planned rest: bar full
        assertEquals(e.clock.millis() + 75_000, e.store.saved?.endAtMillis)
        t.adjust(-30)
        assertEquals(45, t.running().remainingSeconds)
        assertEquals(75, t.running().totalSeconds)
        advanceTimeBy(45_000); runCurrent()
        assertTrue(t.state.value is RestTimerState.Finished)
    }

    @Test
    fun adjust_keepsPlannedTotalSoProgressFollows() = runTest {
        val e = env(); val t = e.timer()
        t.start(180)
        advanceTimeBy(80_000); runCurrent()
        assertEquals(100, t.running().remainingSeconds)
        t.adjust(-15)
        assertEquals(85, t.running().remainingSeconds)
        assertEquals(180, t.running().totalSeconds)
        t.adjust(15); t.adjust(15)
        assertEquals(115, t.running().remainingSeconds)
        assertEquals(180, t.running().totalSeconds)
    }

    @Test
    fun adjust_plusBeyondPlannedGrowsTotal() = runTest {
        val e = env(); val t = e.timer()
        t.start(180)
        advanceTimeBy(5_000); runCurrent()
        t.adjust(15)
        assertEquals(190, t.running().remainingSeconds)
        assertEquals(190, t.running().totalSeconds)
    }

    @Test
    fun adjust_clampAtZeroFinishes() = runTest {
        val e = env(); val t = e.timer()
        t.start(10)
        advanceTimeBy(2_000); runCurrent()
        t.adjust(-15)
        assertTrue(t.state.value is RestTimerState.Finished)
        assertNull(e.store.saved)
    }

    @Test
    fun adjust_whenIdleIsNoOp() = runTest {
        val t = env().timer()
        t.adjust(15)
        assertEquals(RestTimerState.Idle, t.state.value)
    }

    @Test
    fun startWhileRunning_replaces() = runTest {
        val e = env(); val t = e.timer()
        t.start(60, "a")
        advanceTimeBy(20_000); runCurrent()
        t.start(90, "b")
        val s = t.running()
        assertEquals(90, s.remainingSeconds)
        assertEquals("b", s.label)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(30, t.running().remainingSeconds)
    }

    @Test
    fun restore_futureEndResumesRunning() = runTest {
        val e = env()
        e.store.saved = SavedRestTimer(e.clock.millis() + 42_000, 60, "Panca")
        val t = e.timer()
        val s = t.running()
        assertEquals(42, s.remainingSeconds)
        assertEquals(60, s.totalSeconds)
        assertEquals("Panca", s.label)
        advanceTimeBy(42_000); runCurrent()
        assertTrue(t.state.value is RestTimerState.Finished)
        assertNull(e.store.saved)
    }

    @Test
    fun restore_pastEndIsFinishedRestoredAndStoreCleared() = runTest {
        val e = env()
        e.store.saved = SavedRestTimer(e.clock.millis() - 1, 60, "Panca")
        val t = e.timer()
        assertEquals(RestTimerState.Finished(60, "Panca", restored = true), t.state.value)
        assertNull(e.store.saved)
    }

    @Test
    fun ticker_emitsOncePerSecondAndStopsWhenIdle() = runTest {
        val e = env(); val t = e.timer()
        val seen = mutableListOf<Int>()
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
            t.state.collect { if (it is RestTimerState.Running) seen.add(it.remainingSeconds) }
        }
        t.start(5)
        advanceTimeBy(3_500); runCurrent()
        assertEquals(listOf(5, 4, 3, 2), seen)
        t.skip()
        runCurrent()
        // no pending ticker work left: nothing scheduled after skip
        advanceTimeBy(10_000); runCurrent()
        assertEquals(listOf(5, 4, 3, 2), seen)
        assertEquals(RestTimerState.Idle, t.state.value)
    }
}
