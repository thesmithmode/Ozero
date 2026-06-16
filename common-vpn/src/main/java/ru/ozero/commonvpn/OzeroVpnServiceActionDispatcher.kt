package ru.ozero.commonvpn

import ru.ozero.enginescore.PersistentLoggers

internal class OzeroVpnServiceActionDispatcher(
    private val latestStartIdSetter: (Int) -> Unit,
    private val isChainOrchestratorReady: () -> Boolean,
    private val enterForeground: () -> Boolean,
    private val isTunnelIdle: () -> Boolean,
    private val clearStopping: () -> Unit,
    private val stopSelf: (Int) -> Unit,
    private val startVpn: () -> Unit,
    private val stopVpn: () -> Unit,
    private val restartVpn: () -> Unit,
) {
    fun dispatch(action: String?, startId: Int): Int {
        val foregroundOk = enterForeground()
        if (!foregroundOk) {
            stopSelf(startId)
            return OzeroVpnServiceStartResult.NOT_STICKY
        }
        return try {
            if (!isChainOrchestratorReady()) {
                PersistentLoggers.error(TAG, "chainOrchestrator not injected - Hilt graph failure")
                stopSelf(startId)
                return OzeroVpnServiceStartResult.NOT_STICKY
            }
            latestStartIdSetter(startId)
            when (action) {
                OzeroVpnService.ACTION_STOP -> stopVpn()
                OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG -> {
                    if (isTunnelIdle()) {
                        PersistentLoggers.warn(TAG, "runtime config restart ignored because tunnel is idle")
                        stopSelf(startId)
                    } else {
                        restartVpn()
                    }
                }
                OzeroVpnService.ACTION_START, null -> {
                    clearStopping()
                    startVpn()
                }
            }
            OzeroVpnServiceStartResult.STICKY
        } catch (t: Throwable) {
            PersistentLoggers.error(TAG, "onStartCommand threw: ${t.message}")
            runCatching { stopVpn() }
            OzeroVpnServiceStartResult.NOT_STICKY
        }
    }

    private companion object {
        const val TAG = "OzeroVpnService"
    }
}

internal object OzeroVpnServiceStartResult {
    const val STICKY = android.app.Service.START_STICKY
    const val NOT_STICKY = android.app.Service.START_NOT_STICKY
}
