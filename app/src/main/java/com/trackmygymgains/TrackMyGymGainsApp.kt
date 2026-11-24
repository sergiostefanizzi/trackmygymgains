package com.trackmygymgains

import android.app.Application

class TrackMyGymGainsApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
