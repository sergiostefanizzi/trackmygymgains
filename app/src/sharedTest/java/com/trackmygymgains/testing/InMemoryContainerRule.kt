package com.trackmygymgains.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackmygymgains.AppContainer
import com.trackmygymgains.TrackMyGymGainsApp
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.plan.ParsedPlan
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Installs an [AppContainer] backed by an in-memory database before the activity starts, so tests do not
 * depend on the device database. Use it as the OUTER rule of a RuleChain around the compose rule.
 * [seedPlan], when given, is imported as the active plan.
 */
class InMemoryContainerRule(private val seedPlan: ParsedPlan? = null) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val app = ApplicationProvider.getApplicationContext<Context>() as TrackMyGymGainsApp
            val container = AppContainer(app) { ctx ->
                Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
            }
            app.setContainerForTesting(container)
            try {
                if (seedPlan != null) {
                    runBlocking { container.planRepository.importPlan(seedPlan, "scheda.xlsx") }
                }
                base.evaluate()
            } finally {
                app.setContainerForTesting(null)
                container.database.close()
            }
        }
    }
}
