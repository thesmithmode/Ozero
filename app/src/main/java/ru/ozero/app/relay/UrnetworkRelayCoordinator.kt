package ru.ozero.app.relay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkDefaults
import ru.ozero.engineurnetwork.UrnetworkJwtBootstrapper
import ru.ozero.engineurnetwork.UrnetworkProvideControlMode
import ru.ozero.engineurnetwork.UrnetworkProvideNetworkMode
import ru.ozero.engineurnetwork.byClientJwt
import ru.ozero.engineurnetwork.provideNetworkMode
import ru.ozero.engineurnetwork.walletAddress
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.PersistentLoggers
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class UrnetworkRelayCoordinator(
    private val bridge: UrnetworkSdkBridge,
    private val configStore: UrnetworkConfigStore,
    private val tunnelController: TunnelController,
    private val jwtBootstrapper: UrnetworkJwtBootstrapper,
    private val networkMonitor: RelayNetworkMonitor? = null,
    private val relayLockManager: RelayLockManager? = null,
    private val pipeFactory: DummyPipeFactory = AndroidDummyPipeFactory,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val jobRef = AtomicReference<Job?>(null)
    private val watchdogRef = AtomicReference<Job?>(null)
    private val relayOwned = AtomicBoolean(false)
    private val bootstrapAttemptedThisSession = AtomicBoolean(false)
    private val pipeWriteEndRef = AtomicReference<AutoCloseable?>(null)

    fun start() {
        val newJob = combine(
            tunnelController.state,
            configStore.byClientJwt(),
            configStore.walletAddress(),
        ) { tunnelState, byClientJwt, walletAddress ->
            Triple(tunnelState, byClientJwt, walletAddress)
        }
            .distinctUntilChanged()
            .onEach { (tunnelState, byClientJwt, walletAddress) ->
                handleState(tunnelState, byClientJwt, walletAddress)
            }
            .launchIn(scope)
        jobRef.getAndSet(newJob)?.cancel()
    }

    private suspend fun handleState(
        tunnelState: TunnelState,
        byClientJwt: String?,
        walletAddress: String,
    ) {
        if (tunnelState !is TunnelState.Connected) {
            bootstrapAttemptedThisSession.set(false)
            if (relayOwned.compareAndSet(true, false)) {
                PersistentLoggers.debug(TAG, "mesh session: tunnel offline — releasing worker")
                stopWatchdog()
                runCatching { networkMonitor?.stop() }
                runCatching { relayLockManager?.release() }
                runCatching { bridge.stop() }
                closeDummyPipe()
            }
            return
        }
        if (tunnelState.engineId == EngineId.URNETWORK) {
            relayOwned.set(false)
            return
        }
        if (byClientJwt == null) {
            if (bootstrapAttemptedThisSession.compareAndSet(false, true)) {
                PersistentLoggers.debug(
                    TAG,
                    "mesh session: credential missing while ${tunnelState.engineId} active — acquiring",
                )
                val r = jwtBootstrapper.ensureClientJwt()
                if (r is UrnetworkJwtBootstrapper.Result.Failed) {
                    PersistentLoggers.warn(TAG, "mesh session: credential acquisition failed: ${r.reason}")
                } else {
                    PersistentLoggers.debug(TAG, "mesh session: credential acquired (${r.javaClass.simpleName})")
                }
            }
            return
        }
        val result = startBridgeWithRetry(walletAddress, byClientJwt)
        if (result is UrnetworkSdkBridge.StartResult.Success) {
            relayOwned.set(true)
            attachDummyIoLoop()
            val controlMode = UrnetworkProvideControlMode.ALWAYS
            runCatching { bridge.setProvidePaused(false) }
                .onFailure { PersistentLoggers.warn(TAG, "mesh session: worker pause toggle threw: ${it.message}") }
            runCatching { bridge.setProvideControlMode(controlMode) }
                .onFailure { PersistentLoggers.warn(TAG, "mesh session: setProvideControlMode threw: ${it.message}") }
            val networkMode = runCatching { configStore.provideNetworkMode().first() }
                .getOrDefault(UrnetworkProvideNetworkMode.WIFI)
            runCatching { bridge.setProvideNetworkMode(networkMode) }
                .onFailure { PersistentLoggers.warn(TAG, "mesh session: setProvideNetworkMode threw: ${it.message}") }
            runCatching { networkMonitor?.start(networkMode, true) }
                .onFailure { PersistentLoggers.warn(TAG, "mesh session: networkMonitor threw: ${it.message}") }
            runCatching { relayLockManager?.acquire() }
                .onFailure { PersistentLoggers.warn(TAG, "mesh session: lockManager threw: ${it.message}") }
            val diag = runCatching { bridge.relayDiagnostics() }.getOrDefault("unavailable")
            PersistentLoggers.debug(
                TAG,
                "mesh session: worker started alongside ${tunnelState.engineId} " +
                    "(provideEnabled=true controlMode=${controlMode.rawValue} " +
                    "networkMode=${networkMode.rawValue}) diag=[$diag]",
            )
            startWatchdog()
        } else {
            PersistentLoggers.warn(TAG, "mesh session: worker start failed: $result")
        }
    }

    private suspend fun attachDummyIoLoop() {
        try {
            val pipe = pipeFactory.create()
            pipeWriteEndRef.set(pipe.writeEnd)
            val attachResult = bridge.attachRelayTun(pipe.readFd)
            if (attachResult is UrnetworkSdkBridge.AttachResult.Success) {
                PersistentLoggers.info(TAG, "mesh session: dummy IoLoop attached (upstream offline-mode pattern)")
            } else {
                PersistentLoggers.warn(TAG, "mesh session: dummy IoLoop attach failed: $attachResult")
                closeDummyPipe()
            }
        } catch (t: Throwable) {
            PersistentLoggers.warn(TAG, "mesh session: dummy IoLoop threw: ${t.message}")
            closeDummyPipe()
        }
    }

    private fun closeDummyPipe() {
        pipeWriteEndRef.getAndSet(null)?.let { writeEnd ->
            runCatching { writeEnd.close() }
                .onFailure { PersistentLoggers.warn(TAG, "pipe write-end close threw: ${it.message}") }
        }
    }

    private fun startWatchdog() {
        stopWatchdog()
        val job = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                if (!relayOwned.get()) break
                val diag = runCatching { bridge.relayDiagnostics() }.getOrNull()
                if (diag == null || diag == "device=null") {
                    PersistentLoggers.warn(TAG, "watchdog: bridge unhealthy ($diag) — resetting")
                    runCatching { networkMonitor?.stop() }
                    runCatching { relayLockManager?.release() }
                    runCatching { bridge.stop() }
                    closeDummyPipe()
                    relayOwned.set(false)
                    break
                }
            }
        }
        watchdogRef.set(job)
    }

    private fun stopWatchdog() {
        watchdogRef.getAndSet(null)?.cancel()
    }

    private suspend fun startBridgeWithRetry(
        walletAddress: String,
        byClientJwt: String,
    ): UrnetworkSdkBridge.StartResult? {
        RETRY_BACKOFF_MS.forEachIndexed { attempt, delayMs ->
            val result = runCatching {
                bridge.start(
                    walletAddress = walletAddress,
                    apiUrl = UrnetworkDefaults.DEFAULT_API_URL,
                    connectUrl = UrnetworkDefaults.DEFAULT_CONNECT_URL,
                    byClientJwt = byClientJwt,
                )
            }.getOrNull()
            if (result is UrnetworkSdkBridge.StartResult.Success) return result
            val isLast = attempt == RETRY_BACKOFF_MS.lastIndex
            PersistentLoggers.warn(
                TAG,
                "mesh session: start attempt ${attempt + 1}/${RETRY_BACKOFF_MS.size} failed: $result" +
                    if (isLast) " — giving up" else " — retry in ${delayMs / 1000}s",
            )
            if (!isLast) {
                delay(delayMs)
                if (!scope.isActive) return null
            }
        }
        return null
    }

    fun stop() {
        jobRef.getAndSet(null)?.cancel()
        stopWatchdog()
        closeDummyPipe()
        runCatching { networkMonitor?.stop() }
        runCatching { relayLockManager?.release() }
    }

    private companion object {
        const val TAG = "MeshSession"
        val RETRY_BACKOFF_MS = longArrayOf(5_000L, 30_000L, 90_000L)
        const val WATCHDOG_INTERVAL_MS = 60_000L
    }
}
