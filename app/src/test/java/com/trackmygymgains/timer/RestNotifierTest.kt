package com.trackmygymgains.timer

import android.app.Notification
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestNotifierTest {
    private lateinit var env: RestTestEnv

    @Before fun setUp() { env = RestTestEnv() }

    @After fun tearDown() { env.close() }

    @Test
    fun running_postsOngoingCountdownNotificationAndSchedulesAlarm() {
        env.timer.start(120, "Squat")
        val end = env.clock.now + 120_000

        val n = env.notification(RestNotifier.NOTIFICATION_RUNNING)
        assertNotNull(n)
        assertEquals("Squat", n!!.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(end, n.`when`)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(3, n.actions.size)
        assertEquals(end, env.alarms.peekNextScheduledAlarm()?.triggerAtMs)
    }

    @Test
    fun running_withoutLabelUsesDefaultTitle() {
        env.timer.start(30)
        val n = env.notification(RestNotifier.NOTIFICATION_RUNNING)!!
        assertEquals("Recupero", n.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun adjust_reschedulesAlarmAndUpdatesCountdown() {
        env.timer.start(120, "Squat")
        env.timer.adjust(15)
        val end = env.clock.now + 135_000
        assertEquals(end, env.alarms.peekNextScheduledAlarm()?.triggerAtMs)
        assertEquals(1, env.alarms.scheduledAlarms.size)
        assertEquals(end, env.notification(RestNotifier.NOTIFICATION_RUNNING)!!.`when`)
    }

    @Test
    fun skip_cancelsNotificationAndAlarm() {
        env.timer.start(120, "Squat")
        env.timer.skip()
        assertNull(env.notification(RestNotifier.NOTIFICATION_RUNNING))
        assertNull(env.alarms.peekNextScheduledAlarm())
    }

    @Test
    fun finished_cancelsOngoingNotification() {
        env.timer.start(60, "Squat")
        env.advance(60)
        assertTrue(env.timer.state.value is RestTimerState.Finished)
        assertNull(env.notification(RestNotifier.NOTIFICATION_RUNNING))
    }

    @Test
    fun adjustToZero_finishesEarlyCancelsAlarmAndNotifiesEnd() {
        env.timer.start(10, "Squat")
        env.timer.adjust(-15)
        assertTrue(env.timer.state.value is RestTimerState.Finished)
        assertNull(env.alarms.peekNextScheduledAlarm())
        assertNull(env.notification(RestNotifier.NOTIFICATION_RUNNING))
        assertNotNull(env.notification(RestNotifier.NOTIFICATION_DONE))
    }
}
