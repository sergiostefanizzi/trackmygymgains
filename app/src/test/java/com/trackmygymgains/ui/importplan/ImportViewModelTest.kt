package com.trackmygymgains.ui.importplan

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.trackmygymgains.data.db.AppDatabase
import com.trackmygymgains.data.file.PickedFile
import com.trackmygymgains.data.file.PlanFileException
import com.trackmygymgains.data.file.PlanFileReader
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.testing.OLD_SHEET_NAME
import com.trackmygymgains.testing.REFERENCE_SHEET_NAME
import com.trackmygymgains.testing.XlsxTestBuilder
import com.trackmygymgains.testing.referencePlanExpected
import com.trackmygymgains.testing.referenceSheetXlsx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportViewModelTest {

    private class FakeReader(var result: () -> PickedFile) : PlanFileReader {
        var reads = 0
        override suspend fun read(uri: String): PickedFile {
            reads++
            return result()
        }
    }

    private lateinit var db: AppDatabase
    private lateinit var repo: PlanRepository
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = PlanRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(reader: PlanFileReader) = ImportViewModel(repo, reader, dispatcher)

    private fun reference() = FakeReader { PickedFile("scheda.xlsx", referenceSheetXlsx()) }

    private fun noHeaderSheetXlsx() = XlsxTestBuilder()
        .sheet("Riepilogo") { row(1, "Solo", "testo") }
        .sheet(REFERENCE_SHEET_NAME) {
            cell("A1", "FULL BODY 1")
            row(2, "ESERCIZIO", "SET", "TARGET REP", "TARGET RIR", "REST", "NOTE")
            row(3, "SQUAT", 3.0, "5", "1-2", "3'", "/")
        }
        .build()

    @Test
    fun startsIdle() {
        assertEquals(ImportUiState.Idle, vm(reference()).state.value)
    }

    @Test
    fun pickingFileShowsPreviewOfFirstSheet() {
        val vm = vm(reference())
        vm.onFilePicked("content://x")

        val state = vm.state.value as ImportUiState.Preview
        assertEquals("scheda.xlsx", state.fileName)
        assertEquals(listOf(REFERENCE_SHEET_NAME, OLD_SHEET_NAME), state.sheetNames)
        assertEquals(0, state.selectedSheet)
        assertNull(state.sheetError)
        assertEquals(referencePlanExpected, state.plan!!.days)
        assertTrue(state.canConfirm)
    }

    @Test
    fun selectingAnotherSheetReparsesCachedBytes() {
        val reader = reference()
        val vm = vm(reader)
        vm.onFilePicked("content://x")
        vm.selectSheet(1)

        val state = vm.state.value as ImportUiState.Preview
        assertEquals(1, reader.reads)
        assertEquals(1, state.selectedSheet)
        assertEquals(OLD_SHEET_NAME, state.plan!!.sheetName)
        assertEquals("VECCHIO GIORNO", state.plan!!.days.single().name)
        assertEquals(listOf(REFERENCE_SHEET_NAME, OLD_SHEET_NAME), state.sheetNames)

        vm.selectSheet(0)
        assertEquals(referencePlanExpected, (vm.state.value as ImportUiState.Preview).plan!!.days)
    }

    @Test
    fun sheetWithoutHeaderGivesSheetErrorAndKeepsSelector() {
        val vm = vm(FakeReader { PickedFile("x.xlsx", noHeaderSheetXlsx()) })
        vm.onFilePicked("content://x")

        val state = vm.state.value as ImportUiState.Preview
        assertNull(state.plan)
        assertNotNull(state.sheetError)
        assertEquals(2, state.sheetNames.size)
        assertFalse(state.canConfirm)

        // Confirm does nothing when the sheet is not importable.
        vm.confirm()
        assertTrue(vm.state.value is ImportUiState.Preview)

        // Another sheet can still be chosen.
        vm.selectSheet(1)
        val next = vm.state.value as ImportUiState.Preview
        assertNull(next.sheetError)
        assertTrue(next.canConfirm)
    }

    @Test
    fun nonXlsxBytesGiveError() {
        val vm = vm(FakeReader { PickedFile("note.txt", "hello, not a zip".toByteArray()) })
        vm.onFilePicked("content://x")

        assertEquals(ImportUiState.Error(ImportMessages.NOT_XLSX), vm.state.value)
    }

    @Test
    fun readerFailuresGiveErrors() {
        val vm = vm(FakeReader { throw PlanFileException(PlanFileException.Reason.TOO_BIG) })
        vm.onFilePicked("content://x")
        assertEquals(ImportUiState.Error(ImportMessages.TOO_BIG), vm.state.value)

        val vm2 = vm(FakeReader { throw PlanFileException(PlanFileException.Reason.UNREADABLE) })
        vm2.onFilePicked("content://x")
        assertEquals(ImportUiState.Error(ImportMessages.UNREADABLE), vm2.state.value)

        val vm3 = vm(FakeReader { throw IllegalStateException("boom") })
        vm3.onFilePicked("content://x")
        assertEquals(ImportUiState.Error(ImportMessages.UNREADABLE), vm3.state.value)

        val vm4 = vm(FakeReader { throw PlanFileException(PlanFileException.Reason.UNSUPPORTED_GOOGLE_FILE) })
        vm4.onFilePicked("content://x")
        assertEquals(ImportUiState.Error(ImportMessages.UNSUPPORTED_GOOGLE_FILE), vm4.state.value)
    }

    @Test
    fun confirmImportsPlanAndEmitsImported() = runTest(dispatcher) {
        val vm = vm(reference())
        vm.onFilePicked("content://x")
        vm.confirm()

        assertEquals(ImportUiState.Imported, vm.state.first { it is ImportUiState.Imported || it is ImportUiState.Error })
        val plan = repo.getActivePlan()!!
        assertEquals(REFERENCE_SHEET_NAME, plan.name)
        assertEquals("scheda.xlsx", plan.sourceFileName)
        assertEquals(referencePlanExpected.map { it.name }, plan.days.map { it.name })
        assertEquals(
            referencePlanExpected.map { d -> d.exercises.map { it.name } },
            plan.days.map { d -> d.exercises.map { it.name } },
        )
    }

    @Test
    fun resetReturnsToIdle() {
        val vm = vm(reference())
        vm.onFilePicked("content://x")
        vm.reset()
        assertEquals(ImportUiState.Idle, vm.state.value)
    }
}
