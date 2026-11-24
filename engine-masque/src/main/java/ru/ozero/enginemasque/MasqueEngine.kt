package ru.ozero.enginemasque

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
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
import java.net.InetAddress
import java.net.ServerSocket

class MasqueEngine(
    private val runtime: MasqueRuntime,
    private val store: MasqueProfileStore,
    scope: CoroutineScope,
    private val available: () -> Boolean,
) : EnginePlugin {
    override val id = EngineId.MASQUE

    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = true,
        supportsDoH = false,
        localOnly = false,
        requiresServer = true,
        supportsUpstreamSocks = false,
        requiresRoutedProbe = true,
    )

    private val settings = store.settings().catch { emit(MasqueSettings()) }
        .stateIn(scope, SharingStarted.Eagerly, MasqueSettings())
    private val statistics = MutableStateFlow(EngineStats())

    @Volatile
    private var port: Int = 0

    override fun buildManualConfig(settings: SettingsModel?): EngineConfig? =
        createConfig(this.settings.value, settings)

    override suspend fun buildManualConfigAwaitingStorage(settings: SettingsModel?): EngineConfig? {
        val current = store.settings().catch { emit(MasqueSettings()) }.first()
        return createConfig(current, settings)
    }

    override suspend fun buildProxyConfigAwaitingStorage(settings: SettingsModel?): EngineConfig? =
        buildManualConfigAwaitingStorage(settings)

    private fun createConfig(current: MasqueSettings, settings: SettingsModel?): EngineConfig? {
        val profile = current.profile ?: return null
        if (current.corrupt || !available() || (!current.autoEnabled && settings?.manualEngine != id)) return null
        return EngineConfig.Masque(profile.json, current.transport.name, settings?.ipv6Enabled == true)
    }

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
        if (config !is EngineConfig.Masque || upstream !is Upstream.None) {
            return StartResult.Failure("Invalid MASQUE configuration or upstream")
        }
        return try {
            stop()
            val profile = MasqueProfile.parse(config.profileJson)
            val candidate = allocatePort()
            val transport = MasqueTransport.valueOf(config.transport)
            val result = runtime.start(profile, candidate, transport, config.ipv6Enabled)
            if (result == null) {
                StartResult.Failure("MASQUE handshake failed")
            } else {
                port = candidate
                statistics.value = EngineStats(connectedSince = System.currentTimeMillis())
                StartResult.Success(candidate)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            StartResult.Failure("MASQUE start failed")
        }
    }

    private suspend fun allocatePort(): Int = withContext(Dispatchers.IO) {
        ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { it.localPort }
    }

    override suspend fun stop() {
        port = 0
        statistics.value = EngineStats()
        runtime.stop()
    }

    override fun stopTimeoutMs(): Long = 6_000

    override suspend fun awaitReady(): EnginePlugin.ReadyResult =
        if (runtime.isReady()) {
            EnginePlugin.ReadyResult.Ready
        } else {
            EnginePlugin.ReadyResult.Timeout("MASQUE unavailable")
        }

    override suspend fun probe(): ProbeResult {
        val started = System.nanoTime()
        return if (runtime.isReady()) {
            ProbeResult.Success((System.nanoTime() - started) / 1_000_000)
        } else {
            ProbeResult.Failure("MASQUE routed probe failed", code = ProbeResult.Failure.Code.ROUTED_PROBE_FAILED)
        }
    }

    override fun stats(): Flow<EngineStats> = statistics

    override suspend fun exitNodeStrategy(socksPort: Int): ExitNodeStrategy {
        val activePort = port
        return if (activePort > 0 && activePort == socksPort) {
            ExitNodeStrategy.ViaSocks(LOOPBACK, activePort)
        } else {
            ExitNodeStrategy.Unavailable("MASQUE SOCKS endpoint unavailable")
        }
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
    }
}
