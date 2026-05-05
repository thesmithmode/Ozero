package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import ru.ozero.engineurnetwork.auth.GuestJwtResult
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.RomCompat
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunFdAcceptor
import ru.ozero.enginescore.Upstream

class EngineUrnetwork(
    private val configStore: UrnetworkConfigStore,
    private val sdkBridge: UrnetworkSdkBridge,
    private val authService: UrnetworkAuthService,
) : EnginePlugin, TunFdAcceptor {

    override val id = EngineId.URNETWORK

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = true,
        supportsDoH = false,
        localOnly = false,
        requiresServer = true,
        supportsUpstreamSocks = false,
    )

    private val _stats = MutableStateFlow(EngineStats())

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        require(config is EngineConfig.Urnetwork) { "EngineUrnetwork требует EngineConfig.Urnetwork" }
        require(upstream is Upstream.None) {
            "EngineUrnetwork не принимает upstream — supportsUpstreamSocks=false"
        }

        if (RomCompat.isNubiaRedMagic()) {
            PersistentLoggers.warn(TAG, "Nubia/RedMagic ROM — URnetwork отключён (риск Go GC SIGABRT)")
            return StartResult.Failure(reason = "URnetwork нестабилен на Nubia/RedMagic ROM. Используйте ByeDPI или WARP.")
        }

        val byJwt = ensureGuestJwt() ?: return StartResult.Failure(
            reason = "URnetwork guest jwt acquire failed — нет интернета или сервер недоступен",
        )

        val wallet = configStore.walletAddress().first()
        val isPreset = wallet == UrnetworkDefaults.PRESET_WALLET
        PersistentLoggers.info(
            TAG,
            "start wallet=${wallet.take(WALLET_LOG_PREFIX_LEN)}… isPreset=$isPreset hasJwt=true",
        )

        val bridgeResult = sdkBridge.start(
            walletAddress = wallet,
            apiUrl = UrnetworkDefaults.DEFAULT_API_URL,
            connectUrl = UrnetworkDefaults.DEFAULT_CONNECT_URL,
            byJwt = byJwt,
        )
        return when (bridgeResult) {
            UrnetworkSdkBridge.StartResult.Success -> {
                PersistentLoggers.info(TAG, "started OK")
                StartResult.Success(socksPort = config.socksPort)
            }
            is UrnetworkSdkBridge.StartResult.Failed -> {
                PersistentLoggers.error(TAG, "start failed: ${bridgeResult.reason}")
                StartResult.Failure(reason = bridgeResult.reason)
            }
        }
    }

    override suspend fun stop() {
        PersistentLoggers.info(TAG, "stop")
        sdkBridge.stop()
    }

    override suspend fun probe(): ProbeResult =
        ProbeResult.Failure(reason = "URnetwork не предоставляет SOCKS-интерфейс")

    override fun stats(): Flow<EngineStats> = _stats.asStateFlow()

    override suspend fun attachTun(tunFd: Int): TunAttachResult {
        PersistentLoggers.info(TAG, "attachTun fd=$tunFd")
        return when (val r = sdkBridge.attachTun(tunFd)) {
            UrnetworkSdkBridge.AttachResult.Success -> TunAttachResult.Success
            is UrnetworkSdkBridge.AttachResult.Failed -> TunAttachResult.Failure(r.reason)
        }
    }

    private suspend fun ensureGuestJwt(): String? {
        val existing = configStore.byJwt().first()
        if (existing != null) return existing
        PersistentLoggers.info(TAG, "no byJwt in store — auto-creating guest network")
        return when (val r = authService.acquireGuestJwt()) {
            is GuestJwtResult.Success -> {
                configStore.setByJwt(r.byJwt)
                PersistentLoggers.info(TAG, "guest jwt acquired and persisted")
                r.byJwt
            }
            is GuestJwtResult.Error -> {
                PersistentLoggers.error(TAG, "acquireGuestJwt failed: ${r.message}")
                null
            }
        }
    }

    private companion object {
        const val TAG = "EngineUrnetwork"
        const val WALLET_LOG_PREFIX_LEN = 6
    }
}
