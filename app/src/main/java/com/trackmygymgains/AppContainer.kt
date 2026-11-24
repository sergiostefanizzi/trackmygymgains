package com.trackmygymgains

import android.content.Context
import androidx.room.Room
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.file.ContentResolverPlanFileReader
import com.trackmygymgains.data.file.PlanFileReader
import com.trackmygymgains.data.repo.PlanRepository

/**
 * Manual dependency container: owns the database and repositories. Everything is created lazily.
 * Tests can pass a [databaseFactory] (e.g. in-memory Room) to run against a deterministic database.
 */
class AppContainer(
    context: Context,
    private val databaseFactory: (Context) -> AppDatabase = { ctx ->
        Room.databaseBuilder(ctx, AppDatabase::class.java, "trackmygymgains.db").build()
    },
) {
    private val appContext = context.applicationContext ?: context

    val database: AppDatabase by lazy { databaseFactory(appContext) }

    val planRepository: PlanRepository by lazy { PlanRepository(database) }

    val planFileReader: PlanFileReader by lazy { ContentResolverPlanFileReader(appContext) }
}
