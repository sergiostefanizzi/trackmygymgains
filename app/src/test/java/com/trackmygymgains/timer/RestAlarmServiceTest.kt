package com.trackmygymgains.timer

import android.app.Notification
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
class RestAlarmServiceTest {
    private lateinit var env: RestTestEnv
    private val shadowApp get() = shadowOf(env.app)

    @Before fun setUp() { env = RestTestEnv() }

    @After fun tearDown() { env.close() }

    private fun fire(endAt: Long) =
        RestTimerAlarmReceiver().onReceive(env.app, Intent().putExtra(RestNotifier.EXTRA_END_AT, endAt))

    /** Runs a natural end through the alarm receiver and returns the intent the app asked to start. */
    private fun naturalEndStartIntent(label: String = "Squat"): Intent {
        env.timer.start(60, label)
        val end = env.clock.now + 60_000
        env.advance(60)
        fire(end)
        return shadowApp.nextStartedService
    }

    private fun ring(): ServiceController<RestAlarmService> {
        val intent = naturalEndStartIntent()
        return Robolectric.buildService(RestAlarmService::class.java, intent).create().startCommand(0, 1)
    }

    @Test
    fun naturalEnd_startsAlarmService() {
        val intent = naturalEndStartIntent()
        assertEquals(RestAlarmService::class.java.name, intent.component!!.className)
        assertNull(env.notification(RestNotifier.NOTIFICATION_RUNNING))
    }

    @Test
    fun ringing_vibratesRepeatedlyAndShowsForegroundNotificationWithStop() {
        val c = ring()
        assertTrue(shadowOf(env.vibrator).isVibrating)
        val n = shadowOf(c.get()).lastForegroundNotification
        assertNotNull(n)
        assertEquals("Recupero terminato", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Squat", n.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(1, n.actions.size)
        assertEquals("Ferma", n.actions[0].title.toString())
        // Still vibrating long after a one-shot pattern would be over.
        ShadowLooper.idleMainLooper(30, TimeUnit.SECONDS)
        assertTrue(shadowOf(env.vibrator).isVibrating)
    }

    @Test
    fun stopAction_acknowledgesTimerStopsServiceAndVibration() {
        val c = ring()
        RestTimerActionReceiver().onReceive(env.app, Intent(RestNotifier.ACTION_STOP))
        assertEquals(RestTimerState.Idle, env.timer.state.value)
        assertNotNull(shadowApp.nextStoppedService)
        c.destroy()
        assertFalse(shadowOf(env.vibrator).isVibrating)
    }

    @Test
    fun startingNewRestWhileRinging_stopsService() {
        val c = ring()
        env.timer.start(90, "Panca")
        assertNotNull(shadowApp.nextStoppedService)
        c.destroy()
        assertFalse(shadowOf(env.vibrator).isVibrating)
    }

    @Test
    fun skipWhileRinging_stopsService() {
        ring()
        env.timer.skip()
        assertNotNull(shadowApp.nextStoppedService)
    }

    @Test
    fun skipBeforeEnd_staleAlarmStartsNothing() {
        env.timer.start(60, "Squat")
        val end = env.clock.now + 60_000
        env.timer.skip()
        env.advance(60)
        fire(end)
        assertNull(shadowApp.peekNextStartedService())
        assertFalse(shadowOf(env.vibrator).isVibrating)
    }

    @Test
    fun alarmAfterAcknowledge_startsNothing() {
        env.timer.start(60, "Squat")
        val end = env.clock.now + 60_000
        env.advance(60)
        env.timer.acknowledgeFinished()
        fire(end)
        assertNull(shadowApp.peekNextStartedService())
    }

    @Test
    fun duplicateAlarm_startsServiceOnce() {
        env.timer.start(60, "Squat")
        val end = env.clock.now + 60_000
        env.advance(60)
        fire(end)
        fire(end)
        assertNotNull(shadowApp.nextStartedService)
        assertNull(shadowApp.peekNextStartedService())
    }

    @Test
    fun adjustToZero_startsServiceEarly() {
        env.timer.start(10, "Squat")
        env.timer.adjust(-15)
        assertNotNull(shadowApp.nextStartedService)
    }

    @Test
    fun safetyCap_stopsVibrationAndKeepsPlainNotification() {
        val c = ring()
        ShadowLooper.idleMainLooper(RestAlarmService.MAX_RING_MILLIS - 1_000, TimeUnit.MILLISECONDS)
        assertTrue(shadowOf(env.vibrator).isVibrating)
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        assertFalse(shadowOf(env.vibrator).isVibrating)
        assertTrue(shadowOf(c.get()).isStoppedBySelf)
        val n = env.notification(RestNotifier.NOTIFICATION_DONE)
        assertNotNull(n)
        assertTrue(n!!.actions == null || n.actions.isEmpty())
        c.destroy()
    }

    @Test
    fun restoredFinished_afterProcessDeath_rings() {
        env.timer.start(60, "Squat")
        val end = env.clock.now + 60_000
        env.clock.now = end + 2_000 // process "dies", alarm wakes it later
        val restored = RestTimer(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), env.store, env.clock)
        assertTrue((restored.state.value as RestTimerState.Finished).restored)
        RestEndHandler.resetForTesting()
        RestEndHandler.handle(env.app, end, "Squat", validate = true, timer = restored)
        val intent = shadowApp.nextStartedService
        assertNotNull(intent)
        Robolectric.buildService(RestAlarmService::class.java, intent).create().startCommand(0, 1)
        assertTrue(shadowOf(env.vibrator).isVibrating)
    }
}
