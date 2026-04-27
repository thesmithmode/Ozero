package ru.ozero.coreorchestrator

import ru.ozero.coreapi.EngineId

sealed class OrchestratorTransition {
    data object Connect : OrchestratorTransition()
    data object Disconnect : OrchestratorTransition()
    data class ProbeComplete(val engineId: EngineId) : OrchestratorTransition()
    data class ConnectSuccess(val engineId: EngineId, val socksPort: Int) : OrchestratorTransition()
    data class ConnectFailed(val engineId: EngineId, val reason: String) : OrchestratorTransition()
    data object DisconnectComplete : OrchestratorTransition()
}
