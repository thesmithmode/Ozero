package ru.ozero.enginescore

import kotlinx.coroutines.flow.Flow
import ru.ozero.enginescore.settings.SettingsModel

interface EnginePlugin {
    val id: EngineId
    val capabilities: EngineCapabilities
    suspend fun start(config: EngineConfig, upstream: Upstream = Upstream.None): StartResult
    suspend fun stop()
    suspend fun probe(): ProbeResult
    fun stats(): Flow<EngineStats>
    suspend fun tunSpec(): TunSpec? = null
    fun preflight(): EnginePreflight? = null

    suspend fun awaitReady(): ReadyResult = ReadyResult.Ready

    fun peerWatchdogPolicy(): PeerWatchdogPolicy = PeerWatchdogPolicy()

    data class PeerWatchdogPolicy(
        val timeoutMs: Long = DEFAULT_PEER_WATCHDOG_TIMEOUT_MS,
        val recoverBeforeFirstPeer: Boolean = false,
    )

    sealed class ReadyResult {
        data object Ready : ReadyResult()
        data class Timeout(val reason: String) : ReadyResult()
    }

    suspend fun exitNodeStrategy(socksPort: Int): ExitNodeStrategy =
        ExitNodeStrategy.Unavailable("exit node strategy unavailable")

    suspend fun ipProbeRoute(socksPort: Int): IpProbeRoute = when (val strategy = exitNodeStrategy(socksPort)) {
        ExitNodeStrategy.DirectHttp -> IpProbeRoute.Default
        is ExitNodeStrategy.ViaSocks -> IpProbeRoute.Socks(strategy.host, strategy.port)
        is ExitNodeStrategy.LocationOnly -> IpProbeRoute.StaticLocation(strategy.country, strategy.countryCode)
        is ExitNodeStrategy.ProviderLabel -> IpProbeRoute.StaticLocation(strategy.label, null)
        is ExitNodeStrategy.AutoSelected -> IpProbeRoute.AutoSelected
        is ExitNodeStrategy.Unavailable -> IpProbeRoute.Unavailable(strategy.reason)
    }

    fun stopTimeoutMs(): Long = DEFAULT_STOP_TIMEOUT_MS

    fun buildManualConfig(settings: SettingsModel?): EngineConfig? = null

    fun buildProxyConfig(settings: SettingsModel?): EngineConfig? = buildManualConfig(settings)

    fun statsLabel(stats: EngineStats): String? {
        val peers = stats.activeConnections
        return if (peers > 0) "$peers conns" else null
    }

    suspend fun recover(): RecoverResult = RecoverResult.NotSupported

    sealed class RecoverResult {
        data object Success : RecoverResult()
        data class Failed(val reason: String) : RecoverResult()
        data object NotSupported : RecoverResult()
    }

    companion object {
        const val DEFAULT_STOP_TIMEOUT_MS = 2_000L
        const val DEFAULT_PEER_WATCHDOG_TIMEOUT_MS = 30_000L
    }
}
