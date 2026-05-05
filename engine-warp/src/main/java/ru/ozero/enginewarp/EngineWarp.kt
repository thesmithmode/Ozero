package ru.ozero.enginewarp

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

class EngineWarp(
    private val autoConfig: WarpAutoConfig,
    private val configStore: WarpConfigSlotStore,
    private val sdkBridge: WarpSdkBridge,
) : EnginePlugin {

    override val id = EngineId.WARP

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = true,
        supportsDoH = false,
        localOnly = false,
        requiresServer = false,
        supportsUpstreamSocks = false,
    )

    private val _stats = MutableStateFlow(EngineStats())

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        require(config is EngineConfig.Warp) { "EngineWarp требует EngineConfig.Warp" }
        require(upstream is Upstream.None) {
            "EngineWarp не принимает upstream — supportsUpstreamSocks=false"
        }

        val cached = configStore.activeConfig().first()
        val effective = if (cached != null) {
            PersistentLoggers.info(TAG, "start using active config")
            cached
        } else {
            PersistentLoggers.info(TAG, "no active config — calling autoConfig.register")
            val regResult = autoConfig.register()
            val fresh = regResult.getOrElse { t ->
                val msg = t.message ?: "register failed"
                PersistentLoggers.error(TAG, "register failure: $msg")
                return StartResult.Failure(reason = "WARP register failed: $msg", cause = t)
            }
            configStore.addSlot("WARP Auto", fresh)
            fresh
        }

        return when (val r = sdkBridge.start(effective)) {
            WarpSdkBridge.StartResult.Success -> {
                PersistentLoggers.info(TAG, "started OK")
                StartResult.Success(socksPort = WARP_NO_SOCKS_PORT)
            }
            is WarpSdkBridge.StartResult.Failed -> {
                PersistentLoggers.error(TAG, "bridge start failed: ${r.reason}")
                StartResult.Failure(reason = r.reason)
            }
        }
    }

    override suspend fun stop() {
        PersistentLoggers.info(TAG, "stop")
        sdkBridge.stop()
    }

    override suspend fun probe(): ProbeResult =
        ProbeResult.Failure(reason = "WARP не предоставляет SOCKS-интерфейс")

    override fun stats(): Flow<EngineStats> = _stats.asStateFlow()

    private companion object {
        const val TAG = "EngineWarp"
        const val WARP_NO_SOCKS_PORT = 0
    }
}
