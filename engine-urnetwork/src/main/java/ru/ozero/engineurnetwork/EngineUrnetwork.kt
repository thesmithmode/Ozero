package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream

class EngineUrnetwork(
    private val configStore: UrnetworkConfigStore,
    private val sdkBridge: UrnetworkSdkBridge,
) : EnginePlugin {

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

        val consent = configStore.consentGranted().first()
        if (!consent) {
            PersistentLoggers.warn(TAG, "start aborted — URnetwork consent not granted")
            return StartResult.Failure(reason = "URnetwork consent not granted")
        }

        val wallet = configStore.walletAddress().first()
        val isPreset = wallet == UrnetworkDefaults.PRESET_WALLET
        PersistentLoggers.info(
            TAG,
            "start wallet=${wallet.take(WALLET_LOG_PREFIX_LEN)}… isPreset=$isPreset",
        )

        val bridgeResult = sdkBridge.start(
            walletAddress = wallet,
            apiUrl = UrnetworkDefaults.DEFAULT_API_URL,
            connectUrl = UrnetworkDefaults.DEFAULT_CONNECT_URL,
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

    private companion object {
        const val TAG = "EngineUrnetwork"
        const val WALLET_LOG_PREFIX_LEN = 6
    }
}
