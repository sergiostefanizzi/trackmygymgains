package com.trackmygymgains

import android.content.Context
import androidx.room.Room
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.file.ContentResolverPlanFileReader
import com.trackmygymgains.data.file.PlanFileReader
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.data.repo.SessionRepository
import com.trackmygymgains.timer.RestNotifier
import com.trackmygymgains.timer.RestTimer
import com.trackmygymgains.timer.RestTimerStore
import com.trackmygymgains.timer.SharedPreferencesRestTimerStore
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container: owns the database and repositories. Everything is created lazily.
 * Tests can pass a [databaseFactory] (e.g. in-memory Room) to run against a deterministic database.
 */
class AppContainer(
    context: Context,
    private val databaseFactory: (Context) -> AppDatabase = { ctx ->
        Room.databaseBuilder(ctx, AppDatabase::class.java, "trackmygymgains.db").build()
    },
    private val restTimerStoreFactory: (Context) -> RestTimerStore = { ctx -> SharedPreferencesRestTimerStore(ctx) },
    private val clock: Clock = Clock.systemUTC(),
    private val zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
) {
    private val appContext = context.applicationContext ?: context

    val database: AppDatabase by lazy { databaseFactory(appContext) }

    val planRepository: PlanRepository by lazy { PlanRepository(database, zone = zone) }

    val sessionRepository: SessionRepository by lazy { SessionRepository(database) }

    val planFileReader: PlanFileReader by lazy { ContentResolverPlanFileReader(appContext) }

    val restTimer: RestTimer by lazy {
        RestTimer(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            store = restTimerStoreFactory(appContext),
            clock = clock,
        )
    }

    /** Mirrors the rest timer into notifications and the end-of-rest alarm. */
    val restNotifier: RestNotifier by lazy {
        RestNotifier(appContext, restTimer, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), clock)
    }
}
