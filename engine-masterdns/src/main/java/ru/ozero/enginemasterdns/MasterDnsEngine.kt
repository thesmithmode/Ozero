package ru.ozero.enginemasterdns

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream

class MasterDnsEngine(
    private val serviceFactory: () -> MasterDnsClientServiceContract,
    private val portAllocator: MasterDnsPortAllocator = MasterDnsPortAllocator(),
    private val resolversProvider: () -> List<String> = { emptyList() },
) : EnginePlugin {

    override val id: EngineId = EngineId.MASTERDNS

    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = false,
        supportsDoH = false,
        localOnly = false,
        requiresServer = true,
        supportsUpstreamSocks = false,
    )

    @Volatile private var service: MasterDnsClientServiceContract? = null

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        val md = config as? EngineConfig.MasterDns
            ?: return StartResult.Failure("expected EngineConfig.MasterDns, got ${config::class.simpleName}")
        val port = portAllocator.allocate(md.socksPort)
        val runtime = MasterDnsRuntimeConfig(md.configToml, md.resolvers, port)
        val activeService = serviceFactory().also { service = it }
        activeService.start(runtime)
        val terminal = activeService.state.first {
            it !is MasterDnsClientState.Starting && it !is MasterDnsClientState.Idle
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
}
