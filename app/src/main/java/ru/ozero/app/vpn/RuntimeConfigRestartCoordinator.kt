package ru.ozero.app.vpn

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
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
    private var restartPending = false
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

    private suspend fun restartVpnIfRunning(reason: String) {
        if (!restartMutex.tryLock()) {
            restartPending = true
            AppLogger.d(TAG, "runtime config restart request coalesced")
            return
        }
        try {
            var nextReason = reason
            do {
                restartPending = false
                if (!performRestartIfRunning(nextReason)) return
                nextReason = "coalesced runtime config changed while restart was running -> restart"
                if (restartPending) {
                    withTimeoutOrNull(RESTART_SETTLE_TIMEOUT_MS) {
                        tunnelController.state.first {
                            it is TunnelState.Connected || it is TunnelState.Failed
                        }
                    }
                }
            } while (restartPending)
        } finally {
            restartMutex.unlock()
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
            sendVpnAction(OzeroVpnService.ACTION_STOP)
            val stopped = withTimeoutOrNull(RESTART_STOP_TIMEOUT_MS) {
                tunnelController.state.first { it is TunnelState.Idle || it is TunnelState.Failed }
            }
            if (stopped == null) {
                AppLogger.w(TAG, "runtime config restart skipped: stop timeout")
                tunnelController.onSwitchingFinished("runtime config restart stop timeout")
                return false
            }
            tunnelController.onSwitchingStarted(from = fromEngine, to = pendingTarget)
            sendVpnAction(OzeroVpnService.ACTION_START)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            tunnelController.onSwitchingFinished("runtime config restart failed: ${e.message}")
            throw e
        }
    }

    private fun sendVpnAction(action: String) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, OzeroVpnService::class.java).apply {
                this.action = action
            },
        )
    }

    private companion object {
        const val TAG = "RuntimeConfigRestartCoordinator"
        const val RESTART_STOP_TIMEOUT_MS = 11_000L
        const val RESTART_SETTLE_TIMEOUT_MS = 15_000L
    }
}
