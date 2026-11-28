package com.trackmygymgains.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.file.PickedFile
import com.trackmygymgains.data.file.PlanFileReader
import com.trackmygymgains.data.model.SetOutcome
import com.trackmygymgains.data.plan.ParsedDay
import com.trackmygymgains.data.plan.ParsedExercise
import com.trackmygymgains.data.plan.ParsedHistoryExercise
import com.trackmygymgains.data.plan.ParsedHistorySession
import com.trackmygymgains.data.plan.ParsedHistorySet
import com.trackmygymgains.data.plan.ParsedPlan
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.data.repo.SessionRepository
import com.trackmygymgains.testing.XlsxTestBuilder
import com.trackmygymgains.timer.InMemoryRestTimerStore
import com.trackmygymgains.timer.RestTimer
import com.trackmygymgains.ui.history.HistoryUiState
import com.trackmygymgains.ui.history.HistoryViewModel
import com.trackmygymgains.ui.importplan.ImportMessages
import com.trackmygymgains.ui.importplan.ImportUiState
import com.trackmygymgains.ui.importplan.ImportViewModel
import com.trackmygymgains.ui.summary.SummaryUiState
import com.trackmygymgains.ui.summary.SummaryViewModel
import com.trackmygymgains.ui.workout.SetFormValues
import com.trackmygymgains.ui.workout.SetInput
import com.trackmygymgains.ui.workout.WorkoutUiState
import com.trackmygymgains.ui.workout.WorkoutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** UI-level behaviour of the history imported from the plan file (preview, result message, list, summary, prefill). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportedHistoryUiTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var plans: PlanRepository
    private lateinit var sessions: SessionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-11-28T10:00:00Z"), ZoneOffset.UTC)
        plans = PlanRepository(db, clock, ZoneOffset.UTC)
        sessions = SessionRepository(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private val days = listOf(
        ParsedDay(
            "FULL BODY 1",
            listOf(
                ParsedExercise("PULL UP", 3, "4-6", "1-2", 180, null),
                ParsedExercise("SQUAT", 3, "5", "2", 120, null),
            ),
        ),
    )

    private val importedSession = ParsedHistorySession(
        dayIndex = 0,
        week = 1,
        date = LocalDate.parse("2026-10-13"),
        exercises = listOf(
            ParsedHistoryExercise(
                0,
                listOf(
                    ParsedHistorySet(40.0, 6, SetOutcome.BUFFER, null),
                    ParsedHistorySet(40.0, 5, SetOutcome.FAILURE, null),
                ),
                "ottima giornata",
            ),
            ParsedHistoryExercise(1, emptyList(), "saltato per il ginocchio"),
        ),
    )

    private suspend fun importWithHistory() {
        plans.importPlan(ParsedPlan("S", days, emptyList(), listOf(importedSession)), "a.xlsx")
    }

    // ---- import preview and result ----

    private fun xlsx(withHistory: Boolean): ByteArray = XlsxTestBuilder().sheet("S") {
        cell(1, 0, "FULL BODY 1")
        if (withHistory) {
            cell(1, 6, "SETTIMANA 1 / DATA: 13/10/2026")
            cell(1, 10, "SETTIMANA 2 / DATA: 21/11/2026")
        }
        row(2, "ESERCIZIO", "SET", "TARGET REP", "TARGET RIR", "REST", "NOTE")
        if (withHistory) {
            for (w in 0..1) {
                listOf("CARICO", "REP", "RIR", "FEEDBACK").forEachIndexed { o, l -> cell(2, 6 + 4 * w + o, "$l W${w + 1}") }
            }
        }
        row(3, "PULL UP", 3.0, "6-8", "1-2", "3'", "/")
        if (withHistory) {
            cell(3, 6, "40 / 40 / 40")
            cell(3, 7, "6 / 6 / 5")
            cell(3, 8, 1.0)
            cell(3, 10, "42 / 42 / 42")
            cell(3, 11, "6 / 6 / 6")
            cell(3, 12, 1.0)
        }
    }.build()

    private fun importVm(bytes: ByteArray) = ImportViewModel(
        plans,
        object : PlanFileReader {
            override suspend fun read(uri: String) = PickedFile("scheda.xlsx", bytes)
        },
        dispatcher,
    )

    @Test
    fun previewPlanCarriesTheHistorySessionsFoundInTheFile() = runTest(dispatcher) {
        val vm = importVm(xlsx(withHistory = true))
        vm.onFilePicked("content://x")

        val preview = vm.state.first { it is ImportUiState.Preview } as ImportUiState.Preview
        val history = preview.plan!!.history
        assertEquals(2, history.size)
        assertEquals(LocalDate.of(2026, 10, 13), history.minOf { it.date })
        assertEquals(LocalDate.of(2026, 11, 21), history.maxOf { it.date })
    }

    @Test
    fun previewWithoutHistoryHasNone() = runTest(dispatcher) {
        val vm = importVm(xlsx(withHistory = false))
        vm.onFilePicked("content://x")

        val preview = vm.state.first { it is ImportUiState.Preview } as ImportUiState.Preview
        assertTrue(preview.plan!!.history.isEmpty())
    }

    @Test
    fun confirmReportsAddedAndAlreadyPresentSessions() = runTest(dispatcher) {
        val bytes = xlsx(withHistory = true)
        val first = importVm(bytes)
        first.onFilePicked("content://x")
        first.state.first { it is ImportUiState.Preview }
        first.confirm()
        assertEquals(
            "Scheda importata · 2 allenamenti dello storico aggiunti",
            (first.state.first { it is ImportUiState.Imported || it is ImportUiState.Error }.let { it as? ImportUiState.Imported ?: error(it.toString()) }).message,
        )

        val again = importVm(bytes)
        again.onFilePicked("content://x")
        again.state.first { it is ImportUiState.Preview }
        again.confirm()
        assertEquals(
            "Scheda importata · storico già presente (2 allenamenti)",
            (again.state.first { it is ImportUiState.Imported || it is ImportUiState.Error }.let { it as? ImportUiState.Imported ?: error(it.toString()) }).message,
        )
    }

    @Test
    fun resultMessageOmitsHistoryWhenBothCountsAreZero() = runTest(dispatcher) {
        assertEquals("Scheda importata", ImportMessages.imported(0, 0))
        assertEquals(
            "Scheda importata · 9 allenamenti dello storico aggiunti, 2 già presenti",
            ImportMessages.imported(9, 2),
        )
        assertEquals(
            "Scheda importata · 1 allenamento dello storico aggiunto, 1 già presente",
            ImportMessages.imported(1, 1),
        )
        assertEquals("Scheda importata · storico già presente (1 allenamento)", ImportMessages.imported(0, 1))

        val vm = importVm(xlsx(withHistory = false))
        vm.onFilePicked("content://x")
        vm.state.first { it is ImportUiState.Preview }
        vm.confirm()
        assertEquals(
            "Scheda importata",
            (vm.state.first { it is ImportUiState.Imported } as ImportUiState.Imported).message,
        )
    }

    // ---- history list, summary, prefill ----

    @Test
    fun historyItemOfImportedSessionIsFlaggedAndKeepsNoRealTime() = runTest(dispatcher) {
        importWithHistory()
        val real = sessions.startSession(plans.getActivePlan()!!.days[0].id)
        sessions.finishSession(real)

        val items = (HistoryViewModel(plans, sessions).state.first { it is HistoryUiState.Content } as HistoryUiState.Content).items

        val imported = items.single { it.imported }
        assertEquals("FULL BODY 1", imported.dayName)
        assertEquals(0L, imported.durationMillis)
        assertEquals(Instant.parse("2026-10-13T12:00:00Z").toEpochMilli(), imported.startedAt)
        assertEquals(2, imported.doneSets)
        assertEquals(1, items.count { !it.imported })
    }

    @Test
    fun summaryOfImportedSessionHasNotesAndFeedbackOnlyExercise() = runTest(dispatcher) {
        importWithHistory()
        val id = sessions.observeFinishedSessions().first().single().id

        val state = SummaryViewModel(id, sessions, plans).state.first { it is SummaryUiState.Ready } as SummaryUiState.Ready

        assertTrue(state.imported)
        assertEquals(Instant.parse("2026-10-13T12:00:00Z").toEpochMilli(), state.startedAt)
        val pullUp = state.exercises[0]
        assertEquals("ottima giornata", pullUp.note)
        assertEquals(null, pullUp.loggedSets[0].rir)
        val squat = state.exercises[1]
        assertTrue(squat.loggedSets.isEmpty())
        assertEquals("saltato per il ginocchio", squat.note)
    }

    @Test
    fun summaryOfAppSessionIsNotImportedAndHasNoNotes() = runTest(dispatcher) {
        plans.importPlan(ParsedPlan("S", days, emptyList()), "a.xlsx")
        val id = sessions.startSession(plans.getActivePlan()!!.days[0].id)
        sessions.finishSession(id)

        val state = SummaryViewModel(id, sessions, plans).state.first { it is SummaryUiState.Ready } as SummaryUiState.Ready

        assertEquals(false, state.imported)
        assertTrue(state.exercises.all { it.note == null })
    }

    @Test
    fun workoutIsPrefilledFromImportedHistoryWithDefaultRirWhenNotRecorded() = runTest(dispatcher) {
        importWithHistory()
        val day = plans.getActivePlan()!!.days[0]
        val pullUp = day.exercises[0]
        val vm = WorkoutViewModel(
            sessions.startSession(day.id),
            sessions,
            plans,
            RestTimer(backgroundScope, InMemoryRestTimerStore(), Clock.systemUTC()),
        )

        val state = vm.state.first { it is WorkoutUiState.Ready } as WorkoutUiState.Ready

        val form = state.exercises.first().currentForm
        assertEquals(SetFormValues("40", "6", SetOutcome.BUFFER, SetInput.defaultRir(pullUp.targetRir)), form)
        assertNull(state.exercises.first().loggedSets.firstOrNull())
    }
}
