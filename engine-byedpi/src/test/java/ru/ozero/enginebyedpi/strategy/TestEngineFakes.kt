package ru.ozero.enginebyedpi.strategy

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream

internal class AlwaysSucceedEngine : EnginePlugin {
    override val id = EngineId.BYEDPI
    override val capabilities = EngineCapabilities(
        supportsTcp = true, supportsUdp = false, supportsDoH = false,
        localOnly = true, requiresServer = false, supportsUpstreamSocks = false,
    )
    override fun stats(): Flow<EngineStats> = MutableStateFlow(EngineStats())
    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
        StartResult.Success(socksPort = 1080)
    override suspend fun stop() = Unit
    override suspend fun probe(): ru.ozero.enginescore.ProbeResult =
        ru.ozero.enginescore.ProbeResult.Success(latencyMs = 0)
}

internal class AlwaysFailEngine : EnginePlugin {
    override val id = EngineId.BYEDPI
    override val capabilities = EngineCapabilities(
        supportsTcp = true, supportsUdp = false, supportsDoH = false,
        localOnly = true, requiresServer = false, supportsUpstreamSocks = false,
    )
    override fun stats(): Flow<EngineStats> = MutableStateFlow(EngineStats())
    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
        StartResult.Failure("test fail")
    override suspend fun stop() = Unit
    override suspend fun probe(): ru.ozero.enginescore.ProbeResult =
        ru.ozero.enginescore.ProbeResult.Success(latencyMs = 0)
}

internal class CountingEngine : EnginePlugin {
    var startCount = 0
    var stopCount = 0
    override val id = EngineId.BYEDPI
    override val capabilities = EngineCapabilities(
        supportsTcp = true, supportsUdp = false, supportsDoH = false,
        localOnly = true, requiresServer = false, supportsUpstreamSocks = false,
    )
    override fun stats(): Flow<EngineStats> = MutableStateFlow(EngineStats())
    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        startCount++
        return StartResult.Success(socksPort = 1080)
    }
    override suspend fun stop() {
        stopCount++
    }
    override suspend fun probe(): ru.ozero.enginescore.ProbeResult =
        ru.ozero.enginescore.ProbeResult.Success(latencyMs = 0)
}

internal class HangingStartEngine : EnginePlugin {
    var startCount = 0
    var stopCount = 0
    override val id = EngineId.BYEDPI
    override val capabilities = EngineCapabilities(
        supportsTcp = true, supportsUdp = false, supportsDoH = false,
        localOnly = true, requiresServer = false, supportsUpstreamSocks = false,
    )
    override fun stats(): Flow<EngineStats> = MutableStateFlow(EngineStats())
    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        startCount++
        delay(Long.MAX_VALUE)
        return StartResult.Failure("unreachable")
    }
    override suspend fun stop() {
        stopCount++
    }
    override suspend fun probe(): ru.ozero.enginescore.ProbeResult =
        ru.ozero.enginescore.ProbeResult.Success(latencyMs = 0)
}

internal class PortRecordingEngine : EnginePlugin {
    val ports = mutableListOf<Int>()
    override val id = EngineId.BYEDPI
    override val capabilities = EngineCapabilities(
        supportsTcp = true, supportsUdp = false, supportsDoH = false,
        localOnly = true, requiresServer = false, supportsUpstreamSocks = false,
    )
    override fun stats(): Flow<EngineStats> = MutableStateFlow(EngineStats())
    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        val byedpi = config as EngineConfig.ByeDpi
        ports += byedpi.socksPort
        return StartResult.Success(socksPort = byedpi.socksPort)
    }
    override suspend fun stop() = Unit
    override suspend fun probe(): ru.ozero.enginescore.ProbeResult =
        ru.ozero.enginescore.ProbeResult.Success(latencyMs = 0)
}

internal class AlwaysSucceedProbe : SocksProbeClient {
    override suspend fun probe(site: String): ProbeResult =
        ProbeResult(site = site, success = true, durationMs = 1L)
}

internal class AlwaysFailProbe : SocksProbeClient {
    override suspend fun probe(site: String): ProbeResult =
        ProbeResult(site = site, success = false, durationMs = 1L)
}
