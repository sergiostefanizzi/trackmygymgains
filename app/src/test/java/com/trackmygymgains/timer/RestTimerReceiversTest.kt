package com.trackmygymgains.timer

import android.app.Notification
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class RestTimerActionReceiverTest {
    private lateinit var env: RestTestEnv

    @Before fun setUp() { env = RestTestEnv() }

    @After fun tearDown() { env.close() }

    private fun send(action: String) =
        RestTimerActionReceiver().onReceive(env.app, Intent(action))

    @Test
    fun plusAndMinusAdjustTheTimer() {
        env.timer.start(120, "Squat")
        send(RestNotifier.ACTION_PLUS)
        assertEquals(135, (env.timer.state.value as RestTimerState.Running).remainingSeconds)
        send(RestNotifier.ACTION_MINUS)
        send(RestNotifier.ACTION_MINUS)
        assertEquals(105, (env.timer.state.value as RestTimerState.Running).remainingSeconds)
    }

    @Test
    fun skipStopsTheTimer() {
        env.timer.start(120, "Squat")
        send(RestNotifier.ACTION_SKIP)
        assertEquals(RestTimerState.Idle, env.timer.state.value)
    }
}

@RunWith(AndroidJUnit4::class)
class RestTimerAlarmReceiverTest {
    private lateinit var env: RestTestEnv

    @Before fun setUp() { env = RestTestEnv() }

    @After fun tearDown() { env.close() }

    private fun fire(endAt: Long) =
        RestTimerAlarmReceiver().onReceive(env.app, Intent().putExtra(RestNotifier.EXTRA_END_AT, endAt))

    @Test
    fun naturalEnd_vibratesAndPostsRestFinishedNotification() {
        env.timer.start(60, "Squat")
        val end = env.clock.now + 60_000
        env.advance(60)
        fire(end)

        assertTrue(shadowOf(env.vibrator).isVibrating)
        val n = env.notification(RestNotifier.NOTIFICATION_DONE)
        assertNotNull(n)
        assertEquals("Recupero terminato", n!!.extras.getString(Notification.EXTRA_TITLE))
        assertNull(env.notification(RestNotifier.NOTIFICATION_RUNNING))
    }

    @Test
    fun duplicateAlarmDoesNotVibrateTwice() {
        env.timer.start(60, "Squat")
        val end = env.clock.now + 60_000
        env.advance(60)
        fire(end)
        env.vibrator.cancel()
        fire(end)
        assertFalse(shadowOf(env.vibrator).isVibrating)
    }

    @Test
    fun staleAlarmAfterSkip_isIgnored() {
        env.timer.start(60, "Squat")
        val end = env.clock.now + 60_000
        env.timer.skip()
        env.advance(60)
        fire(end)

        assertFalse(shadowOf(env.vibrator).isVibrating)
        assertNull(env.notification(RestNotifier.NOTIFICATION_DONE))
    }

    @Test
    fun staleAlarmAfterAdjust_isIgnored() {
        env.timer.start(60, "Squat")
        val oldEnd = env.clock.now + 60_000
        env.timer.adjust(15)
        env.clock.now = oldEnd // old alarm fires while the timer still has 15 s left
        fire(oldEnd)

        assertFalse(shadowOf(env.vibrator).isVibrating)
        assertNull(env.notification(RestNotifier.NOTIFICATION_DONE))
    }

    @Test
    fun alarmAfterAcknowledgedFinish_stillFiresOnce() {
        env.timer.start(60, "Squat")
        val end = env.clock.now + 60_000
        env.advance(60)
        env.timer.acknowledgeFinished()
        fire(end)
        assertTrue(shadowOf(env.vibrator).isVibrating)
    }
}
