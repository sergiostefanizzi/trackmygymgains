package com.trackmygymgains

import android.app.Application
import androidx.annotation.VisibleForTesting
import com.trackmygymgains.timer.RestNotifier

class TrackMyGymGainsApp : Application() {
    private var overrideContainer: AppContainer? = null
    private val defaultContainer: AppContainer by lazy { AppContainer(this) }
    private var notifierContainer: AppContainer? = null

    val container: AppContainer get() = overrideContainer ?: defaultContainer

    override fun onCreate() {
        super.onCreate()
        RestNotifier.createChannels(this)
        // Also covers a process started by an alarm or a notification action: the restored timer is mirrored again.
        startNotifier()
    }

    /** Replaces the container (null restores the default). Must be called before the activity is created. */
    @VisibleForTesting
    fun setContainerForTesting(container: AppContainer?) {
        overrideContainer = container
        startNotifier()
    }

    private fun startNotifier() {
        val current = container
        if (notifierContainer === current) return
        notifierContainer?.restNotifier?.stop()
        notifierContainer = current
        current.restNotifier.start()
    }
}
