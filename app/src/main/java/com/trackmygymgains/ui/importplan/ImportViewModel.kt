package com.trackmygymgains.ui.importplan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.trackmygymgains.TrackMyGymGainsApp
import com.trackmygymgains.data.file.PickedFile
import com.trackmygymgains.data.file.PlanFileException
import com.trackmygymgains.data.file.PlanFileReader
import com.trackmygymgains.data.plan.ParseWarning
import com.trackmygymgains.data.plan.ParsedPlan
import com.trackmygymgains.data.plan.PlanParseException
import com.trackmygymgains.data.plan.WorkoutPlanParser
import com.trackmygymgains.data.repo.PlanRepository
import com.trackmygymgains.data.xlsx.XlsxException
import com.trackmygymgains.data.xlsx.XlsxWorkbook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

sealed interface ImportUiState {
    data object Idle : ImportUiState

    data object Loading : ImportUiState

    /**
     * A file was read. [plan] is the parse of [selectedSheet]; when that sheet cannot be parsed,
     * [plan] is null and [sheetError] explains why (the user can still pick another sheet).
     */
    data class Preview(
        val fileName: String?,
        val sheetNames: List<String>,
        val selectedSheet: Int,
        val plan: ParsedPlan?,
        val sheetError: String?,
        val warnings: List<ParseWarning>,
        val importing: Boolean = false,
    ) : ImportUiState {
        val canConfirm: Boolean get() = plan != null && !importing
    }

    data class Error(val message: String) : ImportUiState

    data object Imported : ImportUiState
}

class ImportViewModel(
    private val planRepository: PlanRepository,
    private val fileReader: PlanFileReader,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    /** True when a plan already exists, so a new import replaces it. */
    val hasActivePlan: StateFlow<Boolean> = planRepository.observeHasActivePlan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var job: Job? = null
    private var workbook: XlsxWorkbook? = null

    /** Called with the uri chosen in the document picker. */
    fun onFilePicked(uri: String) {
        job?.cancel()
        _state.value = ImportUiState.Loading
        job = viewModelScope.launch {
            try {
                val picked = fileReader.read(uri)
                _state.value = withContext(workDispatcher) { open(picked) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                workbook = null
                _state.value = ImportUiState.Error(messageFor(e))
            }
        }
    }

    /** Re-parses the cached file with another sheet. */
    fun selectSheet(index: Int) {
        val current = _state.value as? ImportUiState.Preview ?: return
        val wb = workbook ?: return
        if (index == current.selectedSheet || index !in wb.sheetNames.indices || current.importing) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = withContext(workDispatcher) { parseSheet(wb, index, current.fileName) }
        }
    }

    /** Saves the parsed plan as the active one. */
    fun confirm() {
        val current = _state.value as? ImportUiState.Preview ?: return
        val plan = current.plan ?: return
        if (current.importing) return
        _state.value = current.copy(importing = true)
        job = viewModelScope.launch {
            try {
                planRepository.importPlan(plan, current.fileName)
                workbook = null
                _state.value = ImportUiState.Imported
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ImportUiState.Error(ImportMessages.SAVE_FAILED)
            }
        }
    }

    /** Back to the initial (pick a file) state. */
    fun reset() {
        job?.cancel()
        workbook = null
        _state.value = ImportUiState.Idle
    }

    private fun open(picked: PickedFile): ImportUiState {
        val wb = XlsxWorkbook.open(ByteArrayInputStream(picked.bytes))
        if (wb.sheetNames.isEmpty()) throw XlsxException.Corrupted("Workbook has no sheets")
        workbook = wb
        return parseSheet(wb, 0, picked.displayName)
    }

    private fun parseSheet(wb: XlsxWorkbook, index: Int, name: String?): ImportUiState.Preview {
        fun preview(plan: ParsedPlan?, error: String?) = ImportUiState.Preview(
            fileName = name,
            sheetNames = wb.sheetNames,
            selectedSheet = index,
            plan = plan,
            sheetError = error,
            warnings = plan?.warnings.orEmpty(),
        )
        return try {
            preview(WorkoutPlanParser.parse(wb, index), null)
        } catch (e: PlanParseException) {
            preview(null, e.message ?: ImportMessages.SHEET_UNREADABLE)
        } catch (e: XlsxException) {
            preview(null, ImportMessages.SHEET_UNREADABLE)
        }
    }

    private fun messageFor(e: Exception): String = when (e) {
        is PlanFileException -> when (e.reason) {
            PlanFileException.Reason.TOO_BIG -> ImportMessages.TOO_BIG
            PlanFileException.Reason.UNREADABLE -> ImportMessages.UNREADABLE
        }
        is XlsxException.NotAnXlsx -> ImportMessages.NOT_XLSX
        is XlsxException -> ImportMessages.CORRUPTED
        else -> ImportMessages.UNREADABLE
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as TrackMyGymGainsApp).container
                ImportViewModel(container.planRepository, container.planFileReader)
            }
        }
    }
}
