package ru.ozero.app.vpn

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.app.logging.AppLogger
import ru.ozero.commonvpn.OzeroVpnService
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimeConfigRestartCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observer: EngineRuntimeConfigRestartObserver,
    private val tunnelController: TunnelController,
) {
    private val restartMutex = Mutex()
    private val restartQueue = ArrayDeque<String>()
    private var restartInProgress = false
    private var started = false

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        observer.start(
            scope = scope,
            exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                AppLogger.e(TAG, "runtime config restart observer failed", throwable)
            },
            state = tunnelController.state,
            restart = ::restartVpnIfRunning,
        )
    }

    private suspend fun restartVpnIfRunning(reason: String): Boolean {
        var shouldProcess = false
        restartMutex.withLock {
            restartQueue.addLast(reason)
            if (!restartInProgress) {
                restartInProgress = true
                shouldProcess = true
            }
        }
        if (!shouldProcess) {
            AppLogger.d(TAG, "runtime config restart request coalesced")
            return false
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

    private suspend fun performRestartIfRunning(reason: String): Boolean {
        val current = tunnelController.state.value
        val fromEngine = when (current) {
            is TunnelState.Connected -> current.engineId
            is TunnelState.Connecting -> current.engineId
            is TunnelState.Probing -> current.engineId
            else -> return false
        }
        AppLogger.i(TAG, reason)
        val pendingTarget = tunnelController.switching.value?.to
        tunnelController.onSwitchingStarted(from = fromEngine, to = pendingTarget)
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
            val restarted = withTimeoutOrNull(RESTART_START_TIMEOUT_MS) {
                tunnelController.state.first {
                    it is TunnelState.Probing ||
                        it is TunnelState.Connecting ||
                        it is TunnelState.Connected ||
                        it is TunnelState.Failed
                }
            }
            return when (restarted) {
                is TunnelState.Probing,
                is TunnelState.Connecting,
                is TunnelState.Connected -> {
                    tunnelController.onSwitchingFinished("runtime config restart started")
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

    private fun sendVpnAction(action: String) {
        context.startService(
            Intent(context, OzeroVpnService::class.java).apply {
                this.action = action
            },
        )
    }

    private companion object {
        const val TAG = "RuntimeConfigRestartCoordinator"
        const val RESTART_STOP_TIMEOUT_MS = 11_000L
        const val RESTART_START_TIMEOUT_MS = 15_000L
        const val RESTART_SETTLE_TIMEOUT_MS = 15_000L
    }
}
