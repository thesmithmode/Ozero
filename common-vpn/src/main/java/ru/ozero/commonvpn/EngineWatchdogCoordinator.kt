package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class EngineWatchdogCoordinator(
    private val scope: CoroutineScope,
    private val healthMonitor: HealthMonitor,
    private val enginePlugins: Set<EnginePlugin>,
    private val tunnelController: TunnelController,
    private val chainOrchestrator: ChainOrchestrator,
    private val notificationFactory: OzeroNotificationFactory,
    private val tunFdRef: AtomicReference<ParcelFileDescriptor?>,
    private val lockdownStartupFdRef: AtomicReference<ParcelFileDescriptor?>,
    private val statsJobRef: AtomicReference<Job?>,
    private val stopping: AtomicBoolean,
    private val starting: AtomicBoolean,
    private val killswitchProvider: () -> Boolean,
    private val restartInProgressProvider: () -> Boolean = { false },
    private val stopVpnRequest: () -> Unit,
) {

    private val healthWatchJobRef = AtomicReference<Job?>(null)
    private val peerWatchJobRef = AtomicReference<Job?>(null)
    private val stagnationWatchJobRef = AtomicReference<Job?>(null)

    fun startHealthKillswitchWatcher(engineId: EngineId) {
        healthWatchJobRef.getAndSet(null)?.cancel()
        val job = scope.launch {
            try {
                healthMonitor.status
                    .filter { it == HealthMonitor.Status.DEGRADED }
                    .first()
                if (killswitchProvider() && hasBlockingTunForKillswitch() && !stopping.get()) {
                    PersistentLoggers.warn(
                        TAG,
                        "health degraded → killswitch fire engine=$engineId",
                    )
                    enterKillswitchMode(engineId, "health degraded")
                } else {
                    PersistentLoggers.warn(
                        TAG,
                        "health degraded но killswitch off (cached=${killswitchProvider()}) — без partial-shutdown",
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                PersistentLoggers.warn(TAG, "health killswitch watcher threw: ${t.message}")
            }
        }
        healthWatchJobRef.set(job)
    }

    fun startPeerWatchdog(engineId: EngineId) {
        peerWatchJobRef.getAndSet(null)?.cancel()
        val plugin = enginePlugins.firstOrNull { it.id == engineId } ?: return
        val peerWatchdogPolicy = plugin.peerWatchdogPolicy()
        val job = scope.launch {
            try {
                var zeroPeersPolls = 0
                var hadPeers = false
                while (isActive) {
                    delay(PEER_WATCHDOG_POLL_MS)
                    val peers = plugin.stats().first().activeConnections
                    if (peers > 0) {
                        hadPeers = true
                        zeroPeersPolls = 0
                        continue
                    }
                    val timeoutMs = peerWatchdogPolicy.timeoutMs
                    if (!hadPeers && !peerWatchdogPolicy.recoverBeforeFirstPeer) continue
                    zeroPeersPolls += 1
                    if (zeroPeersPolls * PEER_WATCHDOG_POLL_MS < timeoutMs) continue
                    PersistentLoggers.warn(
                        TAG,
                        "peer watchdog: 0 peers ${timeoutMs / 1000}s → recover",
                    )
                    val result = runCatching { plugin.recover() }.getOrElse { t ->
                        EnginePlugin.RecoverResult.Failed("recover threw: ${t.message}")
                    }
                    when (result) {
                        EnginePlugin.RecoverResult.Success -> {
                            zeroPeersPolls = 0
                        }
                        EnginePlugin.RecoverResult.NotSupported -> {
                            PersistentLoggers.warn(
                                TAG,
                                "recover NotSupported engine=$engineId — watchdog остановлен, " +
                                    "VPN продолжает работать (юзер увидит degraded indicator в UI и сам решит rebind)",
                            )
                            return@launch
                        }
                        is EnginePlugin.RecoverResult.Failed -> {
                            PersistentLoggers.warn(TAG, "recover failed: ${result.reason} — продолжаем retry")
                            zeroPeersPolls = 0
                        }
                    }
                    delay(PEER_WATCHDOG_RECOVER_GRACE_MS)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                PersistentLoggers.warn(TAG, "peer watchdog threw: ${t.message}")
            }
        }
        peerWatchJobRef.set(job)
    }

    fun startStagnationWatchdog(engineId: EngineId) {
        stagnationWatchJobRef.getAndSet(null)?.cancel()
        val plugin = enginePlugins.firstOrNull { it.id == engineId } ?: return
        val job = scope.launch {
            try {
                var consecutivePolls = 0
                while (isActive) {
                    delay(STAGNATION_POLL_MS)
                    if (tunnelController.stagnant.value) {
                        consecutivePolls++
                        if (consecutivePolls * STAGNATION_POLL_MS < STAGNATION_RECOVER_THRESHOLD_MS) continue
                        PersistentLoggers.warn(
                            TAG,
                            "stagnation watchdog: traffic flat ${STAGNATION_RECOVER_THRESHOLD_MS / 1000}s → recover",
                        )
                        val result = runCatching { plugin.recover() }.getOrElse { t ->
                            EnginePlugin.RecoverResult.Failed("recover threw: ${t.message}")
                        }
                        when (result) {
                            EnginePlugin.RecoverResult.Success -> consecutivePolls = 0
                            EnginePlugin.RecoverResult.NotSupported -> return@launch
                            is EnginePlugin.RecoverResult.Failed -> {
                                PersistentLoggers.warn(TAG, "stagnation recover failed: ${result.reason}")
                                consecutivePolls = 0
                            }
                        }
                        delay(STAGNATION_RECOVER_GRACE_MS)
                    } else {
                        consecutivePolls = 0
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                PersistentLoggers.warn(TAG, "stagnation watchdog threw: ${t.message}")
            }
        }
        stagnationWatchJobRef.set(job)
    }

    fun cancelWatchers() {
        healthWatchJobRef.getAndSet(null)?.cancel()
        peerWatchJobRef.getAndSet(null)?.cancel()
        stagnationWatchJobRef.getAndSet(null)?.cancel()
    }

    fun handleEngineFailure(engineId: EngineId, reason: String) {
        if (stopping.get()) return
        if (!isActiveEngine(engineId)) {
            PersistentLoggers.warn(TAG, "ignore inactive engine failure: engine=$engineId reason=$reason")
            return
        }
        if (killswitchProvider() && hasBlockingTunForKillswitch()) {
            enterKillswitchMode(engineId, reason)
        } else {
            tunnelController.onEngineDied(engineId, reason)
            stopVpnRequest()
        }
    }

    private fun hasBlockingTun(): Boolean =
        tunFdRef.get() != null || lockdownStartupFdRef.get() != null

    private fun hasBlockingTunForKillswitch(): Boolean =
        hasBlockingTun() || restartInProgressProvider()

    private fun isActiveEngine(engineId: EngineId): Boolean = when (val state = tunnelController.state.value) {
        is TunnelState.Probing -> state.engineId == null || state.engineId == engineId
        is TunnelState.Connecting -> state.engineId == engineId
        is TunnelState.Connected -> state.engineId == engineId
        is TunnelState.Failed -> state.engineId == engineId
        else -> false
    }

    private fun enterKillswitchMode(engineId: EngineId, reason: String) {
        PersistentLoggers.warn(TAG, "killswitch engaging: engine=$engineId reason=$reason")
        tunnelController.onKillswitchEngaged(engineId, reason)
        statsJobRef.getAndSet(null)?.cancel()
        healthWatchJobRef.getAndSet(null)?.cancel()
        peerWatchJobRef.getAndSet(null)?.cancel()
        stagnationWatchJobRef.getAndSet(null)?.cancel()
        scope.launch {
            runCatching { chainOrchestrator.stop() }
                .onFailure { t ->
                    PersistentLoggers.warn(TAG, "killswitch: chainOrchestrator.stop threw: ${t.message}")
                }
        }
        runCatching { healthMonitor.stop() }
        notificationFactory.notifyStats("Killswitch активен — трафик заблокирован")
        starting.set(false)
    }

    companion object {
        private const val TAG = "EngineWatchdogCoordinator"
        const val PEER_WATCHDOG_POLL_MS = 5_000L
        const val PEER_WATCHDOG_TIMEOUT_MS = 30_000L
        const val PEER_WATCHDOG_RECOVER_GRACE_MS = 30_000L
        const val STAGNATION_POLL_MS = 10_000L
        const val STAGNATION_RECOVER_THRESHOLD_MS = 60_000L
        const val STAGNATION_RECOVER_GRACE_MS = 30_000L
    }
}
