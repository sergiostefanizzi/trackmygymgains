package com.trackmygymgains

import android.app.Application

class TrackMyGymGainsApp : Application() {
    // Will host the AppContainer (manual DI) in a later milestone.
    val container: AppContainer by lazy { AppContainer() }
}
