package ru.ozero.app.vpn

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.app.logging.AppLogger
import ru.ozero.commonvpn.EngineStartupBudget
import ru.ozero.commonvpn.OzeroVpnService
import ru.ozero.commonvpn.StartSequenceCoordinator
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.TrafficMode
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Singleton

@Singleton
class RuntimeConfigRestartCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observer: EngineRuntimeConfigRestartObserver,
    private val tunnelController: TunnelController,
    private val settingsRepository: SettingsRepository,
) {
    private val restartMutex = Mutex()
    private data class AutoRestart(val candidates: List<EngineId>, val proxyMode: Boolean)
    private data class RestartRequest(val reason: String, val target: EngineId?, val auto: AutoRestart? = null)

    private val restartQueue = ArrayDeque<RestartRequest>()
    private var restartInProgress = false
    private val started = AtomicBoolean(false)

    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            AppLogger.e(TAG, "VPN restart observer failed", throwable)
        }
        observer.start(
            scope = scope,
            exceptionHandler = exceptionHandler,
            state = tunnelController.state,
            restart = ::restartVpnIfRunning,
        )
        val settingsObserver = EngineSettingsRestartObserver(
            settingsFlow = settingsRepository.settings,
            vpnStateProvider = { tunnelController.state.value },
            onRestartConnected = { snapshot ->
                val auto = snapshot.engineAutoPriority?.let { AutoRestart(it, snapshot.trafficMode == TrafficMode.PROXY) }
                restartVpnIfRunning(RestartRequest(snapshot.restartReason(), snapshot.manualEngine, auto))
            },
        )
        scope.launch(exceptionHandler) {
            settingsObserver.triggers.collect { trigger -> settingsObserver.handle(trigger) }
        }
    }

    internal suspend fun restartVpnIfRunning(reason: String): Boolean = restartVpnIfRunning(RestartRequest(reason, null))

    private suspend fun restartVpnIfRunning(request: RestartRequest): Boolean {
        var shouldProcess = false
        restartMutex.withLock {
            restartQueue.addLast(request)
            if (!restartInProgress) {
                restartInProgress = true
                shouldProcess = true
            }
        }
        if (!shouldProcess) {
            AppLogger.d(TAG, "runtime config restart request coalesced")
            return true
        }
        var completed = false
        while (true) {
            val nextReason = restartMutex.withLock {
                restartQueue.removeFirstOrNull()?.also {
                    restartInProgress = true
                } ?: run {
                    restartInProgress = false
                    null
                }
            } ?: return completed
            if (!performRestartIfRunning(nextReason)) {
                abortQueuedRestarts()
                return false
            }
            completed = true
            if (restartMutex.withLock { restartQueue.isNotEmpty() }) {
                withTimeoutOrNull(RESTART_SETTLE_TIMEOUT_MS) {
                    tunnelController.state.first {
                        it is TunnelState.Connected || it is TunnelState.Failed
                    }
                }
            }
        }
    }

    private suspend fun abortQueuedRestarts() {
        restartMutex.withLock {
            restartQueue.clear()
            restartInProgress = false
        }
    }

    private suspend fun performRestartIfRunning(request: RestartRequest): Boolean {
        val current = tunnelController.state.value
        val fromEngine = when (current) {
            is TunnelState.Connected -> current.engineId
            is TunnelState.Connecting -> current.engineId
            is TunnelState.Probing -> current.engineId ?: return false
            else -> return false
        }
        AppLogger.i(TAG, "restart requested ${request.reason}")
        val pendingTarget = tunnelController.switching.value?.to
        val auto = request.auto
        val targetEngine = if (auto == null) request.target ?: pendingTarget ?: fromEngine else null
        val startupTimeoutMs = auto?.let(::autoRestartTimeoutMs) ?: restartStartTimeoutMs(targetEngine)
        if (auto == null) {
            tunnelController.onSwitchingStarted(from = fromEngine, to = targetEngine)
        } else {
            tunnelController.onAutoSwitchingStarted(
                from = fromEngine,
                timeoutMs = startupTimeoutMs + RESTART_STOP_TIMEOUT_MS + RESTART_SETTLE_TIMEOUT_MS,
            )
        }
        try {
            sendVpnAction(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG)
            val stopped = withTimeoutOrNull(RESTART_STOP_TIMEOUT_MS) {
                tunnelController.state.first {
                    it is TunnelState.Disconnecting ||
                        it is TunnelState.Idle ||
                        it is TunnelState.Failed
                }
            }
            if (stopped == null) {
                AppLogger.w(TAG, "runtime config restart skipped: stop timeout")
                tunnelController.onSwitchingFinished("runtime config restart stop timeout")
                return false
            }
            val restarted = if (auto == null) {
                withTimeoutOrNull(startupTimeoutMs) {
                    tunnelController.state.first { it is TunnelState.Connected || it is TunnelState.Failed }
                }
            } else {
                awaitAutoRestarted(startupTimeoutMs)
            }
            return when (restarted) {
                is TunnelState.Connected -> {
                    tunnelController.onSwitchingFinished("runtime config restart connected")
                    true
                }
                is TunnelState.Failed -> {
                    tunnelController.onSwitchingFinished("runtime config restart failed: ${restarted.reason}")
                    false
                }
                null -> {
                    AppLogger.w(TAG, "runtime config restart skipped: start timeout")
                    tunnelController.onSwitchingFinished("runtime config restart start timeout")
                    false
                }
                else -> false
            }
        } catch (e: CancellationException) {
            abortQueuedRestarts()
            throw e
        } catch (e: Exception) {
            abortQueuedRestarts()
            tunnelController.onSwitchingFinished("runtime config restart failed: ${e.message}")
            throw e
        }
    }

    private fun EngineSettingsRestartObserver.Snapshot.restartReason(): String =
        "settings changed: target=${targetEngineForRestart()?.name ?: "AUTO"}"

    private fun EngineSettingsRestartObserver.Snapshot.targetEngineForRestart(): EngineId? =
        manualEngine ?: engineAutoPriority?.firstOrNull()

    private suspend fun awaitAutoRestarted(timeoutMs: Long): TunnelState? = withTimeoutOrNull(timeoutMs) {
        var current = awaitAutoProgress() ?: return@withTimeoutOrNull null
        while (current !is TunnelState.Connected && current !is TunnelState.Failed) {
            current = tunnelController.state.first {
                it is TunnelState.Connected || it is TunnelState.Failed ||
                    it is TunnelState.Idle || it is TunnelState.Disconnecting
            }
            if (current is TunnelState.Idle || current is TunnelState.Disconnecting) {
                current = awaitAutoProgress() ?: return@withTimeoutOrNull null
            }
        }
        current
    }

    private suspend fun awaitAutoProgress(): TunnelState? = withTimeoutOrNull(RESTART_START_TIMEOUT_MS) {
        tunnelController.state.first { it !is TunnelState.Idle && it !is TunnelState.Disconnecting }
    }

    private fun autoRestartTimeoutMs(auto: AutoRestart): Long =
        auto.candidates.distinct().filterNot { it.manualStandaloneOnly }.sumOf { engine ->
            val start = maxOf(
                restartStartTimeoutMs(engine),
                EngineStartupBudget.chainTimeoutMs(engine) + RESTART_START_TIMEOUT_MS,
            )
            val preflight = if (auto.proxyMode) StartSequenceCoordinator.PREFLIGHT_HARD_TIMEOUT_MS else 0L
            start + preflight + RESTART_SETTLE_TIMEOUT_MS
        }.coerceAtLeast(RESTART_START_TIMEOUT_MS)

    private fun restartStartTimeoutMs(target: EngineId?): Long = when (target) {
        EngineId.FPTN -> FPTN_RESTART_START_TIMEOUT_MS
        else -> EngineStartupBudget.restartTimeoutMs(target) ?: RESTART_START_TIMEOUT_MS
    }

    private fun sendVpnAction(action: String) {
        context.startService(
            Intent(action).setClass(context, OzeroVpnService::class.java),
        )
    }

    private companion object {
        const val TAG = "RuntimeConfigRestartCoordinator"
        const val RESTART_STOP_TIMEOUT_MS = 11_000L
        const val RESTART_START_TIMEOUT_MS = 15_000L

        // Longest auth budget (25s) + awaitReady (30s) + state propagation.
        // Shorter than that treats a slow successful FPTN restart as a failure
        // and the next Connected restarts again, because the start is not cancelled.
        const val FPTN_RESTART_START_TIMEOUT_MS = 60_000L
        const val RESTART_SETTLE_TIMEOUT_MS = 15_000L
    }
}
