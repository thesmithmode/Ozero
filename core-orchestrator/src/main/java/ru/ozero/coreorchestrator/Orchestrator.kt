package ru.ozero.coreorchestrator

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Orchestrator {

    private val _state = MutableStateFlow<OrchestratorState>(OrchestratorState.Idle)
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()

    fun dispatch(transition: OrchestratorTransition) {
        // CAS-цикл защищает от гонки при конкурентном dispatch: если кто-то уже изменил
        // состояние между чтением current и записью next — пересчитываем с актуальным.
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

    /**
     * FSM reducer: 13 явных переходов состояний — табличное перечисление,
     * CC отражает число валидных переходов, не реальную сложность. Рефактор
     * (вынос в map/visitor) ухудшит читаемость state machine.
     */
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

            // Switch failed mid-flight → Failed (раньше падало IllegalStateException,
            // crash при network error в момент переключения движка).
            state is OrchestratorState.Switching && transition is OrchestratorTransition.ConnectFailed ->
                OrchestratorState.Failed(transition.engineId, transition.reason)

            // Idempotent stop: повторный Disconnect в Disconnecting/Idle — no-op.
            // Защита от race "user dispatch + auto-stop on probe failure".
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
