package ru.ozero.coreorchestrator

import ru.ozero.coreapi.EngineId

sealed class OrchestratorState {
    data object Idle : OrchestratorState()
    data object Probing : OrchestratorState()
    data class Connecting(val engineId: EngineId) : OrchestratorState()
    data class Connected(val engineId: EngineId, val socksPort: Int) : OrchestratorState()
    data class Failed(val engineId: EngineId, val reason: String) : OrchestratorState()
    data object Disconnecting : OrchestratorState()
}
