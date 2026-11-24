package ru.ozero.engineopera

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.SettingsModel

class OperaEngine(
    private val runtime: OperaProxyRuntime,
    private val binaryAvailable: () -> Boolean,
) : EnginePlugin {
    override val id = EngineId.OPERA
    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = false,
        supportsDoH = false,
        localOnly = false,
        requiresServer = false,
        supportsUpstreamSocks = false,
    )

    private val currentStats = MutableStateFlow(EngineStats())

    override fun buildProxyConfig(settings: SettingsModel?): EngineConfig? {
        if (!binaryAvailable()) return null
        val model = settings ?: SettingsModel.DEFAULT
        return EngineConfig.Opera(model.operaRegion, model.operaSelection)
    }

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        if (config !is EngineConfig.Opera || upstream !is Upstream.None) {
            return StartResult.Failure("invalid Opera configuration or unsupported upstream")
        }
        val region = OperaRegion.entries.firstOrNull { it.name == config.region }
            ?: return StartResult.Failure("invalid Opera region")
        val selection = OperaServerSelection.entries.firstOrNull { it.argument == config.selection }
            ?: return StartResult.Failure("invalid Opera server selection")
        currentStats.value = EngineStats()
        val result = runtime.start(OperaProxyConfig(region, selection))
        if (result is StartResult.Success) {
            currentStats.value = EngineStats(connectedSince = System.currentTimeMillis())
        }
        return result
    }

    override suspend fun stop() {
        runtime.stop()
        currentStats.value = EngineStats()
    }

    override suspend fun probe(): ProbeResult = runtime.probe()

    override fun stats(): Flow<EngineStats> = currentStats

    override suspend fun exitNodeStrategy(socksPort: Int): ExitNodeStrategy =
        if (socksPort in 1..65535 && currentStats.value.connectedSince > 0) {
            ExitNodeStrategy.ViaSocks(OperaProxyCommand.LOOPBACK, socksPort)
        } else {
            ExitNodeStrategy.Unavailable("Opera SOCKS endpoint unavailable")
        }
}
