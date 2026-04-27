package ru.ozero.commonvpn.pipeline

import android.util.Log
import ru.ozero.commonvpn.HevTunnelConfig
import ru.ozero.commonvpn.HevTunnelGateway
import ru.ozero.commonvpn.TunnelController
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.StartResult
import ru.ozero.coreorchestrator.Orchestrator
import ru.ozero.coreorchestrator.OrchestratorState
import ru.ozero.coreorchestrator.OrchestratorTransition
import ru.ozero.coreorchestrator.StrategyEngine

class VpnEnginePipeline(
    private val engines: Map<EngineId, Engine>,
    private val strategy: StrategyEngine,
    private val orchestrator: Orchestrator,
    private val tunnelController: TunnelController,
    private val tunnelGateway: HevTunnelGateway,
    private val socksHost: String = DEFAULT_SOCKS_HOST,
) {

    @Volatile private var currentEngine: Engine? = null

    sealed class Result {
        data class Connected(val engineId: EngineId, val socksPort: Int) : Result()
        data object NoCandidates : Result()
        data class EngineFailed(val engineId: EngineId, val reason: String) : Result()
        data class TunnelFailed(val engineId: EngineId, val code: Int) : Result()
    }

    suspend fun start(tunFd: Int): Result {
        Log.i(TAG, "start tunFd=$tunFd")
        orchestrator.dispatch(OrchestratorTransition.Connect)

        val winner = pickWinner()
        if (winner == null) {
            Log.w(TAG, "no candidates with successful probe")
            orchestrator.dispatch(OrchestratorTransition.Disconnect)
            orchestrator.dispatch(OrchestratorTransition.DisconnectComplete)
            return Result.NoCandidates
        }
        orchestrator.dispatch(OrchestratorTransition.ProbeComplete(winner.engineId))

        val engine = engines[winner.engineId]
            ?: return failConnect(winner.engineId, "engine не найден в DI map")

        return when (val startResult = engine.start(winner.config)) {
            is StartResult.Failure -> failConnect(winner.engineId, startResult.reason)
            is StartResult.Success -> bringTunnelUp(engine, winner.engineId, startResult.socksPort, tunFd)
        }
    }

    suspend fun stop() {
        Log.i(TAG, "stop")
        tunnelGateway.stop()
        currentEngine?.let { eng ->
            runCatching { eng.stop() }
                .onFailure { Log.e(TAG, "engine.stop() threw", it) }
        }
        currentEngine = null
        tunnelController.reset()
        if (orchestrator.state.value !is OrchestratorState.Idle) {
            orchestrator.dispatch(OrchestratorTransition.Disconnect)
            orchestrator.dispatch(OrchestratorTransition.DisconnectComplete)
        }
    }

    private suspend fun pickWinner() = strategy.pickBest(strategy.buildCandidates())

    private fun failConnect(engineId: EngineId, reason: String): Result.EngineFailed {
        Log.w(TAG, "engine $engineId fail: $reason")
        orchestrator.dispatch(OrchestratorTransition.ConnectFailed(engineId, reason))
        return Result.EngineFailed(engineId, reason)
    }

    private suspend fun bringTunnelUp(
        engine: Engine,
        engineId: EngineId,
        socksPort: Int,
        tunFd: Int,
    ): Result {
        val config = HevTunnelConfig(tunFd = tunFd, socksAddress = socksHost, socksPort = socksPort)
        val code = tunnelGateway.start(config)
        if (code != 0) {
            Log.e(TAG, "hev tunnel start failed code=$code, rolling back engine")
            runCatching { engine.stop() }
                .onFailure { Log.e(TAG, "engine.stop rollback threw", it) }
            orchestrator.dispatch(OrchestratorTransition.ConnectFailed(engineId, "tunnel code=$code"))
            return Result.TunnelFailed(engineId, code)
        }
        currentEngine = engine
        tunnelController.onEngineStarted(socksPort)
        orchestrator.dispatch(OrchestratorTransition.ConnectSuccess(engineId, socksPort))
        Log.i(TAG, "connected engine=$engineId socksPort=$socksPort")
        return Result.Connected(engineId, socksPort)
    }

    private companion object {
        const val TAG = "VpnEnginePipeline"
        const val DEFAULT_SOCKS_HOST = "127.0.0.1"
    }
}
