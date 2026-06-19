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
import ru.ozero.enginescore.ExitNodeStrategy
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
        supportsUdp = true,
        supportsDoH = false,
        localOnly = false,
        requiresServer = true,
        supportsUpstreamSocks = false,
    )

    @Volatile
    private var service: MasterDnsClientServiceContract? = null

    @Volatile
    private var activeSocksPort: Int = 0

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
        if (upstream !is Upstream.None) {
            return StartResult.Failure("MasterDNS does not support upstream proxy chaining")
        }
        val port = portAllocator.allocate(md.socksPort)
        val readiness = parseReadinessConfig(md.configToml)
        val runtime = MasterDnsRuntimeConfig(
            configToml = md.configToml,
            resolvers = md.resolvers,
            socksPort = port,
            readinessHost = readiness.host,
            readinessPort = readiness.port,
            readinessTimeoutMs = readiness.timeoutMs,
            readinessPollIntervalMs = readiness.pollIntervalMs,
            readinessConnectTimeoutMs = readiness.connectTimeoutMs,
        )
        val activeService = serviceFactory().also { service = it }
        activeService.start(runtime)
        val terminal = withTimeoutOrNull(startTimeoutMs) {
            activeService.state.first {
                it !is MasterDnsClientState.Starting && it !is MasterDnsClientState.Idle
            }
        } ?: run {
            activeService.stop()
            return StartResult.Failure(
                "masterdns start timeout after ${startTimeoutMs}ms - subprocess stuck in Starting",
            )
        }
        return when (terminal) {
            is MasterDnsClientState.Running -> {
                activeSocksPort = terminal.port
                StartResult.Success(terminal.port)
            }
            is MasterDnsClientState.Error -> StartResult.Failure(terminal.message)
            else -> StartResult.Failure("unexpected state $terminal")
        }
    }

    override suspend fun stop() {
        activeSocksPort = 0
        service?.stop()
        service = null
    }

    override suspend fun probe(): ProbeResult =
        ProbeResult.Failure("masterdns probe not implemented")

    override fun stats(): Flow<EngineStats> = flowOf(EngineStats())

    override suspend fun exitNodeStrategy(socksPort: Int): ExitNodeStrategy {
        val port = activeSocksPort.takeIf { it > 0 } ?: socksPort.takeIf { it > 0 }
        return if (port != null) {
            ExitNodeStrategy.ViaSocks("127.0.0.1", port)
        } else {
            ExitNodeStrategy.Unavailable("MasterDNS SOCKS endpoint unavailable")
        }
    }

    override fun preflight(): EnginePreflight = MasterDnsPreflight(resolversProvider)

    private companion object {
        const val START_TIMEOUT_MS = 10_000L
        val READINESS_STRING_KEYS = setOf("OZERO_READINESS_HOST")
        val READINESS_INT_KEYS = setOf("OZERO_READINESS_PORT", "OZERO_READINESS_CONNECT_TIMEOUT_MS")
        val READINESS_LONG_KEYS = setOf("OZERO_READINESS_TIMEOUT_MS", "OZERO_READINESS_POLL_INTERVAL_MS")

        fun parseReadinessConfig(toml: String): MasterDnsReadinessConfig {
            val stringValues = mutableMapOf<String, String>()
            val intValues = mutableMapOf<String, Int>()
            val longValues = mutableMapOf<String, Long>()
            toml.lines().forEach { line ->
                val parts = line.substringBefore("#").split("=", limit = 2)
                if (parts.size != 2) return@forEach
                val key = parts[0].trim().uppercase()
                val value = parts[1].trim()
                if (key in READINESS_STRING_KEYS) stringValues[key] = value.trim('"').trim()
                if (key in READINESS_INT_KEYS) value.toIntOrNull()?.let { intValues[key] = it }
                if (key in READINESS_LONG_KEYS) value.toLongOrNull()?.let { longValues[key] = it }
            }
            val host = stringValues["OZERO_READINESS_HOST"]
                ?.takeIf { it.isNotBlank() }
                ?: MasterDnsRuntimeConfig.DEFAULT_READINESS_HOST
            val port = intValues["OZERO_READINESS_PORT"]
                ?.takeIf { it in 1..65535 }
                ?: MasterDnsRuntimeConfig.DEFAULT_READINESS_PORT
            val timeoutMs = longValues["OZERO_READINESS_TIMEOUT_MS"]
                ?.takeIf { it > 0 }
                ?: MasterDnsRuntimeConfig.DEFAULT_READINESS_TIMEOUT_MS
            val pollIntervalMs = longValues["OZERO_READINESS_POLL_INTERVAL_MS"]
                ?.takeIf { it > 0 }
                ?: MasterDnsRuntimeConfig.DEFAULT_READINESS_POLL_INTERVAL_MS
            val connectTimeoutMs = intValues["OZERO_READINESS_CONNECT_TIMEOUT_MS"]
                ?.takeIf { it > 0 }
                ?: MasterDnsRuntimeConfig.DEFAULT_READINESS_CONNECT_TIMEOUT_MS
            return MasterDnsReadinessConfig(host, port, timeoutMs, pollIntervalMs, connectTimeoutMs)
        }
    }
}
