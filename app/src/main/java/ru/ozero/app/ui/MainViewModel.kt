package ru.ozero.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.ozero.coreapi.EngineId
import ru.ozero.coreorchestrator.Orchestrator
import ru.ozero.coreorchestrator.OrchestratorState
import ru.ozero.coreorchestrator.OrchestratorTransition
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(private val orchestrator: Orchestrator) : ViewModel() {
    val state: StateFlow<OrchestratorState> =
        orchestrator.state.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = OrchestratorState.Idle,
        )

    fun onConnectClick() {
        val current = state.value
        when (current) {
            is OrchestratorState.Idle, is OrchestratorState.Failed ->
                orchestrator.dispatch(OrchestratorTransition.Connect)
            is OrchestratorState.Connected ->
                orchestrator.dispatch(OrchestratorTransition.Disconnect)
            else -> Unit
        }
    }

    fun onVpnPermissionGranted() {
            }

    fun onVpnPermissionDenied() {
        val current = state.value
        if (current is OrchestratorState.Probing) {
            orchestrator.dispatch(
                OrchestratorTransition.ConnectFailed(
                    engineId = EngineId.BYEDPI,
                    reason = "VPN permission denied",
                ),
            )
        }
    }
}
