package ru.ozero.commonvpn.pipeline

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CancellationException
import ru.ozero.commonvpn.HevTunnelConfig
import ru.ozero.commonvpn.HevTunnelGateway
import ru.ozero.commonvpn.TunnelController
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.PersistentLoggers
import ru.ozero.coreapi.StartResult
import ru.ozero.coreorchestrator.Candidate
import ru.ozero.coreorchestrator.Orchestrator
import ru.ozero.coreorchestrator.OrchestratorState
import ru.ozero.coreorchestrator.OrchestratorTransition
import ru.ozero.coreorchestrator.StrategyEngine
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class VpnEnginePipeline(
    private val engines: Map<EngineId, Engine>,
    private val strategy: StrategyEngine,
    private val orchestrator: Orchestrator,
    private val tunnelController: TunnelController,
    private val tunnelGateway: HevTunnelGateway,
    private val socksHost: String = DEFAULT_SOCKS_HOST,
) {

    private val currentEngine = AtomicReference<Engine?>(null)
    private val tunnelStarted = AtomicBoolean(false)

    sealed class Result {
        data class Connected(val engineId: EngineId, val socksPort: Int) : Result()
        data object NoCandidates : Result()
        data class EngineFailed(val engineId: EngineId, val reason: String) : Result()
        data class TunnelFailed(val engineId: EngineId, val code: Int) : Result()
    }

    suspend fun start(tunPfd: ParcelFileDescriptor): Result {
        Log.i(TAG, "start tunFd=${tunPfd.fd}")
        ensureCleanStart()

        val candidates = strategy.buildCandidates()
        Log.i(TAG, "candidates=${candidates.map { it.engineId }}")
        val winner = strategy.pickBest(candidates)
        Log.i(TAG, "winner=${winner?.engineId ?: "none"}")
        if (winner == null) {
            val fallback = candidates.firstOrNull { it.engineId == EngineId.BYEDPI }
            if (fallback == null) {
                Log.w(TAG, "no BYEDPI candidate available, refusing best-effort fallback to non-BYEDPI engine")
                PersistentLoggers.instance?.warn(TAG, "no BYEDPI candidate — abort")
                orchestrator.dispatch(OrchestratorTransition.Disconnect)
                orchestrator.dispatch(OrchestratorTransition.DisconnectComplete)
                return Result.NoCandidates
            }
            Log.w(TAG, "fallback BYEDPI (best-effort start without probe)")
            orchestrator.dispatch(OrchestratorTransition.ProbeComplete(fallback.engineId))
            return startCandidate(fallback, tunPfd)
        }
        orchestrator.dispatch(OrchestratorTransition.ProbeComplete(winner.engineId))
        return startCandidate(winner, tunPfd)
    }

    private suspend fun startCandidate(candidate: Candidate, tunPfd: ParcelFileDescriptor): Result {
        val engine = engines[candidate.engineId]
            ?: return failConnect(candidate.engineId, "engine не найден в DI map")

        currentEngine.set(engine)
        val startResult = try {
            engine.start(candidate.config)
        } catch (ce: CancellationException) {
            currentEngine.set(null)
            throw ce
        } catch (t: Throwable) {
            currentEngine.set(null)
            return failConnect(candidate.engineId, "engine.start threw: ${t.message}")
        }
        Log.i(TAG, "engine ${candidate.engineId} start → $startResult")
        return when (startResult) {
            is StartResult.Failure -> {
                currentEngine.set(null)
                failConnect(candidate.engineId, startResult.reason)
            }
            is StartResult.Success -> bringTunnelUp(engine, candidate.engineId, startResult.socksPort, tunPfd)
        }
    }

    suspend fun stop() {
        Log.i(TAG, "stop")
        if (tunnelStarted.compareAndSet(true, false)) {
            runCatching { tunnelGateway.stop() }
                .onFailure { Log.e(TAG, "tunnelGateway.stop() threw", it) }
        }
        currentEngine.getAndSet(null)?.let { eng ->
            runCatching { eng.stop() }
                .onFailure { Log.e(TAG, "engine.stop() threw", it) }
        }
        tunnelController.reset()
        if (orchestrator.state.value !is OrchestratorState.Idle) {
            orchestrator.dispatch(OrchestratorTransition.Disconnect)
            orchestrator.dispatch(OrchestratorTransition.DisconnectComplete)
        }
    }

    private suspend fun ensureCleanStart() {
        when (orchestrator.state.value) {
            is OrchestratorState.Idle,
            is OrchestratorState.Failed,
            -> orchestrator.dispatch(OrchestratorTransition.Connect)

            is OrchestratorState.Probing,
            is OrchestratorState.Connecting,
            -> Unit

            else -> {
                Log.w(TAG, "start in dirty state ${orchestrator.state.value::class.simpleName} → physical stop first")
                stop()
                orchestrator.dispatch(OrchestratorTransition.Connect)
            }
        }
    }

    private fun failConnect(engineId: EngineId, reason: String): Result.EngineFailed {
        Log.w(TAG, "engine $engineId fail: $reason")
        orchestrator.dispatch(OrchestratorTransition.ConnectFailed(engineId, reason))
        return Result.EngineFailed(engineId, reason)
    }

    private suspend fun bringTunnelUp(
        engine: Engine,
        engineId: EngineId,
        socksPort: Int,
        tunPfd: ParcelFileDescriptor,
    ): Result {
        Log.i(TAG, "bringTunnelUp entry engine=$engineId socks=$socksPort tunFd=${tunPfd.fd}")
        val config = HevTunnelConfig(tunPfd = tunPfd, socksAddress = socksHost, socksPort = socksPort)
        tunnelStarted.set(true)
        Log.i(TAG, "tunnelGateway.start invoking")
        val code = try {
            tunnelGateway.start(config)
        } catch (t: Throwable) {
            tunnelStarted.set(false)
            currentEngine.set(null)
            runCatching { engine.stop() }
                .onFailure { Log.e(TAG, "engine.stop rollback threw", it) }
            orchestrator.dispatch(OrchestratorTransition.ConnectFailed(engineId, "tunnel threw: ${t.message}"))
            throw t
        }
        if (code != 0) {
            Log.e(TAG, "hev tunnel start failed code=$code, rolling back engine")
            tunnelStarted.set(false)
            currentEngine.set(null)
            runCatching { engine.stop() }
                .onFailure { Log.e(TAG, "engine.stop rollback threw", it) }
            orchestrator.dispatch(OrchestratorTransition.ConnectFailed(engineId, "tunnel code=$code"))
            return Result.TunnelFailed(engineId, code)
        }
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
