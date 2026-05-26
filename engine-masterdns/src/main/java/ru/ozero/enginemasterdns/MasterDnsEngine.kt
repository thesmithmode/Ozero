package ru.ozero.enginemasterdns

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.SettingsModel

class MasterDnsEngine(
    private val serviceFactory: () -> MasterDnsClientServiceContract,
    private val portAllocator: MasterDnsPortAllocator = MasterDnsPortAllocator(),
    private val resolversProvider: () -> List<String> = { emptyList() },
    private val configTomlProvider: () -> String = { "" },
    private val startTimeoutMs: Long = START_TIMEOUT_MS,
) : EnginePlugin {

    override val id: EngineId = EngineId.MASTERDNS

    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = false,
        supportsDoH = false,
        localOnly = false,
        requiresServer = true,
        supportsUpstreamSocks = true,
    )

    @Volatile private var service: MasterDnsClientServiceContract? = null

    override fun buildManualConfig(settings: SettingsModel?): EngineConfig? {
        val toml = configTomlProvider().trim()
        if (toml.isEmpty()) return null
        return EngineConfig.MasterDns(
            configToml = toml,
            resolvers = resolversProvider(),
        )
    }

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        val md = config as? EngineConfig.MasterDns
            ?: return StartResult.Failure("expected EngineConfig.MasterDns, got ${config::class.simpleName}")
        val port = portAllocator.allocate(md.socksPort)
        val upstreamUrl = when (upstream) {
            is Upstream.Socks5 -> "socks5://${upstream.host}:${upstream.port}"
            is Upstream.Http -> "http://${upstream.host}:${upstream.port}"
            is Upstream.None -> null
        }
        val runtime = MasterDnsRuntimeConfig(md.configToml, md.resolvers, port, upstreamUrl)
        val activeService = serviceFactory().also { service = it }
        activeService.start(runtime)
        val terminal = withTimeoutOrNull(startTimeoutMs) {
            activeService.state.first {
                it !is MasterDnsClientState.Starting && it !is MasterDnsClientState.Idle
            }
        } ?: run {
            activeService.stop()
            return StartResult.Failure(
                "masterdns start timeout after ${startTimeoutMs}ms — subprocess stuck in Starting",
            )
        }
        return when (terminal) {
            is MasterDnsClientState.Running -> StartResult.Success(terminal.port)
            is MasterDnsClientState.Error -> StartResult.Failure(terminal.message)
            else -> StartResult.Failure("unexpected state $terminal")
        }
    }

    override suspend fun stop() {
        service?.stop()
        service = null
    }

    override suspend fun probe(): ProbeResult =
        ProbeResult.Failure("masterdns probe not implemented")

    override fun stats(): Flow<EngineStats> = flowOf(EngineStats())

    override fun preflight(): EnginePreflight = MasterDnsPreflight(resolversProvider)

    private companion object {
        const val START_TIMEOUT_MS = 10_000L
    }
}
