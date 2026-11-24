package com.trackmygymgains

import android.app.Application
import androidx.annotation.VisibleForTesting

class TrackMyGymGainsApp : Application() {
    private var overrideContainer: AppContainer? = null
    private val defaultContainer: AppContainer by lazy { AppContainer(this) }

    val container: AppContainer get() = overrideContainer ?: defaultContainer

    /** Replaces the container (null restores the default). Must be called before the activity is created. */
    @VisibleForTesting
    fun setContainerForTesting(container: AppContainer?) {
        overrideContainer = container
    }
}
