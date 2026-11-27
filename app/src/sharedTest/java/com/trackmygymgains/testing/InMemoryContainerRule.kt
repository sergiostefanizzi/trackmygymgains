package com.trackmygymgains.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackmygymgains.AppContainer
import com.trackmygymgains.TrackMyGymGainsApp
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.plan.ParsedPlan
import com.trackmygymgains.timer.InMemoryRestTimerStore
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Installs an [AppContainer] backed by an in-memory database before the activity starts, so tests do not
 * depend on the device database. Use it as the OUTER rule of a RuleChain around the compose rule.
 * [seedPlan], when given, is imported as the active plan.
 * [container] exposes the installed container (all repositories, e.g. sessionRepository) while the test runs.
 */
class InMemoryContainerRule(private val seedPlan: ParsedPlan? = null) : TestRule {
    var container: AppContainer? = null
        private set

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val app = ApplicationProvider.getApplicationContext<Context>() as TrackMyGymGainsApp
            val installed = AppContainer(
                app,
                databaseFactory = { ctx -> Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build() },
                restTimerStoreFactory = { InMemoryRestTimerStore() },
            )
            app.setContainerForTesting(installed)
            container = installed
            try {
                if (seedPlan != null) {
                    runBlocking { installed.planRepository.importPlan(seedPlan, "scheda.xlsx") }
                }
                base.evaluate()
            } finally {
                app.setContainerForTesting(null)
                container = null
                installed.database.close()
            }
        }
    }
}
