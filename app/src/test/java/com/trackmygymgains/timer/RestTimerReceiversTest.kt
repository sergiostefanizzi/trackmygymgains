package com.trackmygymgains.timer

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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

    @Test
    fun stopAcknowledgesFinishedTimer() {
        env.timer.start(60, "Squat")
        env.advance(60)
        send(RestNotifier.ACTION_STOP)
        assertEquals(RestTimerState.Idle, env.timer.state.value)
    }
}
