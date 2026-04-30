package ru.ozero.app.ui.diag

import android.util.Log
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
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

private const val TAG = "DiagnosticsVM"

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val tunnelController: TunnelController,
    private val engine: DiagnosticsEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow<DiagnosticsUiState>(DiagnosticsUiState.NotConnected)
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null

    init {
        viewModelScope.launch {
            tunnelController.state.collectLatest { state ->
                onTunnelState(state)
            }
        }
    }

    fun onRun() {
        val current = tunnelController.state.value
        if (current !is TunnelState.Connected) return
        runJob?.cancel()
        runJob = viewModelScope.launch {
            val total = DiagnosticTargets.URLS.size
            val completed = AtomicInteger(0)
            _uiState.value = DiagnosticsUiState.Running(total = total, completed = 0)
            try {
                val results = engine.runAll(current.socksPort) {
                    _uiState.value = DiagnosticsUiState.Running(
                        total = total,
                        completed = completed.incrementAndGet(),
                    )
                }
                _uiState.value = DiagnosticsUiState.Done(results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "diagnostics run failed", e)
                _uiState.value = DiagnosticsUiState.Done(emptyList())
            }
        }
    }

    fun onStop() {
        runJob?.cancel()
        runJob = null
        if (tunnelController.state.value is TunnelState.Connected) {
            _uiState.value = DiagnosticsUiState.Idle
        } else {
            _uiState.value = DiagnosticsUiState.NotConnected
        }
    }

    private fun onTunnelState(state: TunnelState) {
        if (state is TunnelState.Connected) {
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
}
