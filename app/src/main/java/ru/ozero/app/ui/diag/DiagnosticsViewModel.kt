package ru.ozero.app.ui.diag

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.ozero.coreorchestrator.Orchestrator
import ru.ozero.coreorchestrator.OrchestratorState
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val orchestrator: Orchestrator,
    private val engine: DiagnosticsEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DiagnosticsUiState>(DiagnosticsUiState.NotConnected)
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null

    init {
        viewModelScope.launch {
            orchestrator.state.collectLatest { state ->
                onOrchestratorState(state)
            }
        }
    }

    fun onRun() {
        val current = orchestrator.state.value
        if (current !is OrchestratorState.Connected) return
        runJob?.cancel()
        runJob = viewModelScope.launch {
            _uiState.value = DiagnosticsUiState.Running(total = TOTAL_URLS, completed = 0)
            try {
                val results = engine.runAll(current.socksPort)
                _uiState.value = DiagnosticsUiState.Done(results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = DiagnosticsUiState.Done(emptyList())
            }
        }
    }

    fun onStop() {
        runJob?.cancel()
        runJob = null
        if (orchestrator.state.value is OrchestratorState.Connected) {
            _uiState.value = DiagnosticsUiState.Idle
        } else {
            _uiState.value = DiagnosticsUiState.NotConnected
        }
    }

    private fun onOrchestratorState(state: OrchestratorState) {
        if (state is OrchestratorState.Connected) {
            // Только если ничего не запущено — показываем Idle. Running оставляем как есть.
            if (_uiState.value !is DiagnosticsUiState.Running &&
                _uiState.value !is DiagnosticsUiState.Done
            ) {
                _uiState.value = DiagnosticsUiState.Idle
            }
        } else {
            runJob?.cancel()
            runJob = null
            _uiState.value = DiagnosticsUiState.NotConnected
        }
    }

    private companion object {
        const val TOTAL_URLS = 20
    }
}
