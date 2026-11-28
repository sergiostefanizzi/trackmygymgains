package com.trackmygymgains.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import com.trackmygymgains.AppContainer
import com.trackmygymgains.TrackMyGymGainsApp
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowLooper

/** Clock moved by hand. */
class ManualClock(var now: Long = 1_000_000L) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)
    override fun millis(): Long = now
}

/** Installs a container with an in-memory timer store and a manual clock; the app notifier runs on it. */
class RestTestEnv {
    val app: TrackMyGymGainsApp = ApplicationProvider.getApplicationContext<Context>() as TrackMyGymGainsApp
    val clock = ManualClock()
    val store = InMemoryRestTimerStore()
    private val container = AppContainer(app, restTimerStoreFactory = { store }, clock = clock)
    val timer: RestTimer

    init {
        RestNotifier.createChannels(app)
        RestEndHandler.resetForTesting()
        app.setContainerForTesting(container)
        timer = container.restTimer
    }

    val notifications: NotificationManager get() = app.getSystemService(NotificationManager::class.java)
    val alarms: ShadowAlarmManager get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    val vibrator: Vibrator = app.getSystemService(Vibrator::class.java)

    fun notification(id: Int): Notification? = shadowOf(notifications).getNotification(id)

    /** Moves both the clock and the main looper forward so the ticker runs. */
    fun advance(seconds: Long) {
        clock.now += seconds * 1000
        ShadowLooper.idleMainLooper(seconds, TimeUnit.SECONDS)
    }

    fun close() {
        app.setContainerForTesting(null)
    }
}
