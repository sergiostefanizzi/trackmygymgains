package com.trackmygymgains

import android.content.Context
import androidx.room.Room
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.repo.PlanRepository

/** Manual dependency container: owns the database and repositories. Everything is created lazily. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "trackmygymgains.db").build()
    }

    val planRepository: PlanRepository by lazy { PlanRepository(database) }
}
