package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ShutdownState(
    val tunFdRef: AtomicReference<ParcelFileDescriptor?>,
    val tunIfaceNameRef: AtomicReference<String?>,
    val lockdownStartupFdRef: AtomicReference<ParcelFileDescriptor?>,
    val sessionStartMsRef: AtomicReference<Long>,
    val sessionIdRef: AtomicReference<Long>,
    val startJobRef: AtomicReference<Job?>,
    val shutdownJobRef: AtomicReference<Job?>,
    val starting: AtomicBoolean,
    val stopping: AtomicBoolean,
    val stopSignal: AtomicBoolean,
)

class ShutdownCollaborators(
    val tunnelController: TunnelController,
    val healthMonitor: HealthMonitor,
    val chainOrchestrator: ChainOrchestrator,
    val tunnelGateway: HevTunnelGateway,
    val statsLogger: TunnelStatsLogger,
    val engineWatchdog: EngineWatchdogCoordinator,
    val sessionStatsRecorder: SessionStatsRecorder,
)

class ShutdownCoordinator(
    private val scope: CoroutineScope,
    private val deps: ShutdownCollaborators,
    private val state: ShutdownState,
    private val latestStartIdProvider: () -> Int,
    private val stopForegroundRequest: () -> Unit,
    private val stopSelfRequest: (Int) -> Unit,
) {

    fun stopVpn() {
        if (!state.stopping.compareAndSet(false, true)) return
        state.stopSignal.set(true)
        PersistentLoggers.info(TAG, "stopVpn entry")
        runCatching { deps.tunnelController.onKillswitchReleased() }
        val priorState = deps.tunnelController.state.value
        deps.tunnelController.onDisconnecting()
        state.startJobRef.getAndSet(null)?.cancel()
        deps.statsLogger.cancel()
        deps.engineWatchdog.cancelWatchers()
        state.lockdownStartupFdRef.getAndSet(null)?.runCatching { close() }
        runCatching { deps.healthMonitor.stop() }
        val endStatus = if (priorState is TunnelState.Failed) {
            SessionStatsRecorder.Status.FAILED
        } else {
            SessionStatsRecorder.Status.DISCONNECTED
        }
        recordSessionEnd(endStatus)
        val job = scope.launch { performShutdown() }
        state.shutdownJobRef.set(job)
    }

    suspend fun performShutdown(callStopSelf: Boolean = true) {
        PersistentLoggers.info(TAG, "performShutdown begin")
        try {
            deps.statsLogger.cancel()

            val chainStopJob = scope.launch {
                runCatching { deps.chainOrchestrator.stop() }
                    .onFailure { PersistentLoggers.warn(TAG, "chainOrchestrator.stop threw: ${it.message}") }
            }
            val nativeStopThread = Thread({
                runCatching { deps.tunnelGateway.stop() }
                    .onFailure { PersistentLoggers.warn(TAG, "tunnelGateway.stop threw: ${it.message}") }
            }, "ozero-native-stop").also {
                it.isDaemon = true
                it.start()
            }
            val stopStart = System.currentTimeMillis()
            val chainOk = withTimeoutOrNull(PARALLEL_STOP_TIMEOUT_MS) { chainStopJob.join() }
            if (chainOk == null) {
                PersistentLoggers.warn(TAG, "chainOrchestrator.stop hung > ${PARALLEL_STOP_TIMEOUT_MS}ms")
                chainStopJob.cancel()
            } else {
                Log.i(TAG, "chainOrchestrator.stop completed")
            }
            val remaining = PARALLEL_STOP_TIMEOUT_MS - (System.currentTimeMillis() - stopStart)
            if (remaining > 0) nativeStopThread.join(remaining)
            if (nativeStopThread.isAlive) {
                PersistentLoggers.warn(TAG, "native tunnel stop hung — abandon thread")
            } else {
                Log.i(TAG, "native tunnel stop completed")
            }

            runCatching { state.tunFdRef.getAndSet(null)?.close() }
                .onFailure { PersistentLoggers.warn(TAG, "tunFd.close threw: ${it.message}") }
        } finally {
            deps.tunnelController.reset()
            state.starting.set(false)
            state.stopping.set(false)
            state.stopSignal.set(false)
            state.tunIfaceNameRef.set(null)
            stopForegroundRequest()
            if (callStopSelf) stopSelfRequest(latestStartIdProvider())
            PersistentLoggers.info(TAG, "performShutdown end")
        }
    }

    private fun recordSessionEnd(status: SessionStatsRecorder.Status) {
        val id = state.sessionIdRef.getAndSet(-1L)
        if (id < 0) return
        val startMs = state.sessionStartMsRef.getAndSet(0L)
        val nowMs = System.currentTimeMillis()
        val durationMs = if (startMs > 0L) (nowMs - startMs).coerceAtLeast(0L) else 0L
        val lastStats = deps.tunnelController.stats.value
        val rxBytes = lastStats?.rxBytes ?: 0L
        val txBytes = lastStats?.txBytes ?: 0L
        scope.launch {
            runCatching {
                deps.sessionStatsRecorder.endSession(
                    id = id,
                    endedAt = nowMs,
                    rxBytes = rxBytes,
                    txBytes = txBytes,
                    durationMs = durationMs,
                    status = status,
                )
            }.onFailure { PersistentLoggers.warn(TAG, "endSession threw: ${it.message}") }
        }
    }

    companion object {
        private const val TAG = "ShutdownCoordinator"
        const val PARALLEL_STOP_TIMEOUT_MS = 6_000L
    }
}
