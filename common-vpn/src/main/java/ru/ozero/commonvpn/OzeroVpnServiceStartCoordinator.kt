package ru.ozero.commonvpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class OzeroVpnServiceStartCoordinator(
    private val deps: Dependencies,
) {
    fun start() {
        if (!deps.starting.compareAndSet(false, true)) return
        deps.stopSignal.set(false)
        deps.runtimeConfigRestartCancelled.set(false)
        PersistentLoggers.info(TAG, "startVpn entry")
        val externalVpnActive = deps.logActiveExternalVpn()
        deps.closeStaleTun()
        deps.onKillswitchReleased()
        deps.loadTunnelLibrary()
        PersistentLoggers.debug(TAG, "loadOnce done libraryLoaded=${deps.isTunnelLibraryLoaded()}")

        val job = deps.scope.launch {
            try {
                deps.shutdownJobRef.getAndSet(null)?.let { previousShutdown ->
                    PersistentLoggers.debug(TAG, "startVpn: waiting previous shutdown")
                    runCatching {
                        withTimeoutOrNull(deps.shutdownJoinTimeoutMs) { previousShutdown.join() }
                    }
                    PersistentLoggers.debug(TAG, "startVpn: previous shutdown finished")
                }
                if (deps.stopping.get()) {
                    PersistentLoggers.warn(TAG, "startVpn: stopping still active after shutdown join")
                    return@launch
                }
                if (externalVpnActive) {
                    PersistentLoggers.warn(
                        TAG,
                        "external VPN was active, delaying ${deps.externalVpnReleaseDelayMs}ms before establish",
                    )
                    runCatching { delay(deps.externalVpnReleaseDelayMs) }
                }
                deps.startSequence()
            } finally {
                deps.starting.set(false)
                deps.runtimeConfigRestartInProgress.set(false)
            }
        }
        deps.startJobRef.set(job)
    }

    data class Dependencies(
        val scope: CoroutineScope,
        val startJobRef: AtomicReference<Job?>,
        val shutdownJobRef: AtomicReference<Job?>,
        val starting: AtomicBoolean,
        val stopping: AtomicBoolean,
        val stopSignal: AtomicBoolean,
        val runtimeConfigRestartCancelled: AtomicBoolean,
        val runtimeConfigRestartInProgress: AtomicBoolean,
        val shutdownJoinTimeoutMs: Long,
        val externalVpnReleaseDelayMs: Long,
        val closeStaleTun: () -> Unit,
        val onKillswitchReleased: () -> Unit,
        val logActiveExternalVpn: () -> Boolean,
        val loadTunnelLibrary: () -> Unit,
        val isTunnelLibraryLoaded: () -> Boolean,
        val startSequence: suspend () -> Unit,
    )

    private companion object {
        const val TAG = "OzeroVpnService"
    }
}
