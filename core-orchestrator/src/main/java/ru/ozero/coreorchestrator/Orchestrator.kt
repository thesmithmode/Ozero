package ru.ozero.coreorchestrator

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Orchestrator {

    private val _state = MutableStateFlow<OrchestratorState>(OrchestratorState.Idle)
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()

    fun dispatch(transition: OrchestratorTransition) {
                        while (true) {
            val current = _state.value
            val next = reduce(current, transition)
            if (_state.compareAndSet(current, next)) {
                val from = current::class.simpleName
                val tr = transition::class.simpleName
                val to = next::class.simpleName
                Log.i(TAG, "transition $from + $tr → $to")
                return
            }
        }
    }

        @Suppress("CyclomaticComplexMethod")
    private fun reduce(
        state: OrchestratorState,
        transition: OrchestratorTransition,
    ): OrchestratorState =
        when {
            state is OrchestratorState.Idle && transition is OrchestratorTransition.Connect ->
                OrchestratorState.Probing

            state is OrchestratorState.Probing && transition is OrchestratorTransition.ProbeComplete ->
                OrchestratorState.Connecting(transition.engineId)

            state is OrchestratorState.Connecting && transition is OrchestratorTransition.ConnectSuccess ->
                OrchestratorState.Connected(transition.engineId, transition.socksPort)

            state is OrchestratorState.Connecting && transition is OrchestratorTransition.ConnectFailed ->
                OrchestratorState.Failed(transition.engineId, transition.reason)

            state is OrchestratorState.Connected && transition is OrchestratorTransition.SwitchTo ->
                OrchestratorState.Switching(from = state.engineId, to = transition.engineId)

            state is OrchestratorState.Switching && transition is OrchestratorTransition.SwitchComplete ->
                OrchestratorState.Connected(transition.engineId, transition.socksPort)

            state is OrchestratorState.Connected && transition is OrchestratorTransition.Disconnect ->
                OrchestratorState.Disconnecting

            state is OrchestratorState.Failed && transition is OrchestratorTransition.Disconnect ->
                OrchestratorState.Disconnecting

            state is OrchestratorState.Failed && transition is OrchestratorTransition.Connect ->
                OrchestratorState.Probing

            state is OrchestratorState.Probing && transition is OrchestratorTransition.Disconnect ->
                OrchestratorState.Disconnecting

            state is OrchestratorState.Connecting && transition is OrchestratorTransition.Disconnect ->
                OrchestratorState.Disconnecting

            state is OrchestratorState.Switching && transition is OrchestratorTransition.Disconnect ->
                OrchestratorState.Disconnecting

            state is OrchestratorState.Disconnecting && transition is OrchestratorTransition.DisconnectComplete ->
                OrchestratorState.Idle

                                    state is OrchestratorState.Switching && transition is OrchestratorTransition.ConnectFailed ->
                OrchestratorState.Failed(transition.engineId, transition.reason)

                                    state is OrchestratorState.Disconnecting && transition is OrchestratorTransition.Disconnect -> state
            state is OrchestratorState.Idle && transition is OrchestratorTransition.Disconnect -> state

            else -> {
                Log.e(TAG, "invalid: ${state::class.simpleName} + ${transition::class.simpleName}")
                throw IllegalStateException(
                    "Недопустимый переход: ${state::class.simpleName} + ${transition::class.simpleName}"
                )
            }
        }

    private companion object {
        const val TAG = "Orchestrator"
    }
}
