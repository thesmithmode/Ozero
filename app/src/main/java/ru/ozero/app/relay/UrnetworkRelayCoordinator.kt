package ru.ozero.app.relay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkDefaults
import ru.ozero.engineurnetwork.UrnetworkJwtBootstrapper
import ru.ozero.engineurnetwork.byClientJwt
import ru.ozero.engineurnetwork.provideEnabled
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
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    private val jobRef = AtomicReference<Job?>(null)
    private val relayOwned = AtomicBoolean(false)
    private val bootstrapAttemptedThisSession = AtomicBoolean(false)

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
                PersistentLoggers.info(TAG, "mesh session: tunnel offline — releasing worker")
                runCatching { bridge.stop() }
            }
            return
        }
        if (tunnelState.engineId == EngineId.URNETWORK) {
            relayOwned.set(false)
            return
        }
        if (byClientJwt == null) {
            if (bootstrapAttemptedThisSession.compareAndSet(false, true)) {
                PersistentLoggers.info(
                    TAG,
                    "mesh session: credential missing while ${tunnelState.engineId} active — acquiring",
                )
                val r = jwtBootstrapper.ensureClientJwt()
                if (r is UrnetworkJwtBootstrapper.Result.Failed) {
                    PersistentLoggers.warn(TAG, "mesh session: credential acquisition failed: ${r.reason}")
                } else {
                    PersistentLoggers.info(TAG, "mesh session: credential acquired (${r.javaClass.simpleName})")
                }
            }
            return
        }
        val result = runCatching {
            bridge.start(
                walletAddress = walletAddress,
                apiUrl = UrnetworkDefaults.DEFAULT_API_URL,
                connectUrl = UrnetworkDefaults.DEFAULT_CONNECT_URL,
                byClientJwt = byClientJwt,
            )
        }.getOrNull()
        if (result is UrnetworkSdkBridge.StartResult.Success) {
            relayOwned.set(true)
            val provideEnabled = runCatching { configStore.provideEnabled().first() }.getOrDefault(true)
            runCatching { bridge.setProvidePaused(!provideEnabled) }
                .onFailure { PersistentLoggers.warn(TAG, "mesh session: worker pause toggle threw: ${it.message}") }
            PersistentLoggers.info(
                TAG,
                "mesh session: worker started alongside ${tunnelState.engineId} (provideEnabled=$provideEnabled)",
            )
        } else {
            PersistentLoggers.warn(TAG, "mesh session: worker start failed: $result")
        }
    }

    fun stop() {
        jobRef.getAndSet(null)?.cancel()
    }

    private companion object {
        const val TAG = "MeshSession"
    }
}
