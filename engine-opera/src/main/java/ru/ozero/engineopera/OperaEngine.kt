package ru.ozero.engineopera

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunFdAcceptor
import ru.ozero.enginescore.TunSpec
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.SettingsModel

class OperaEngine(
    private val runtime: OperaProxyRuntime,
    private val tun: OperaTunBridge,
    private val binaryAvailable: () -> Boolean,
    private val closeFd: (Int) -> Unit = { ParcelFileDescriptor.adoptFd(it).close() },
) : EnginePlugin, TunFdAcceptor {
    override val id = EngineId.OPERA
    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = false,
        supportsDoH = true,
        localOnly = false,
        requiresServer = false,
        supportsUpstreamSocks = false,
    )

    private data class Active(val port: Int, val config: EngineConfig.Opera, val attached: Boolean = false)

    private val lifecycle = Mutex()
    private val currentStats = MutableStateFlow(EngineStats())

    @Volatile
    private var active: Active? = null

    override fun buildManualConfig(settings: SettingsModel?): EngineConfig? = config(settings, proxyMode = false)

    override fun buildProxyConfig(settings: SettingsModel?): EngineConfig? = config(settings, proxyMode = true)

    private fun config(settings: SettingsModel?, proxyMode: Boolean): EngineConfig? {
        if (!binaryAvailable()) return null
        val model = settings ?: SettingsModel.DEFAULT
        return EngineConfig.Opera(model.operaRegion, model.operaSelection, proxyMode, model.ipv6Enabled)
    }

    override suspend fun tunSpec(): TunSpec = OperaTunConfig.spec()

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult = lifecycle.withLock {
        if (config !is EngineConfig.Opera || upstream !is Upstream.None) {
            return@withLock StartResult.Failure("invalid Opera configuration or unsupported upstream")
        }
        val region = OperaRegion.entries.firstOrNull { it.name == config.region }
            ?: return@withLock StartResult.Failure("invalid Opera region")
        val selection = OperaServerSelection.entries.firstOrNull { it.argument == config.selection }
            ?: return@withLock StartResult.Failure("invalid Opera server selection")
        stopLocked()
        val result = runtime.start(OperaProxyConfig(region, selection))
        if (result is StartResult.Success) {
            active = Active(result.socksPort, config)
            if (config.proxyMode) markConnected()
        }
        result
    }

    override suspend fun attachTun(tunFd: Int): TunAttachResult = lifecycle.withLock {
        val current = active ?: return@withLock TunAttachResult.Failure("Opera not started")
        if (current.config.proxyMode || current.attached) {
            return@withLock TunAttachResult.Failure("Opera TUN attachment not expected")
        }
        try {
            if (!tun.start(tunFd, OperaTunConfig.build(current.port, current.config.ipv6Enabled))) {
                stopLocked()
                return@withLock TunAttachResult.Failure("Opera TUN runtime failed")
            }
            closeFd(tunFd)
            active = current.copy(attached = true)
            markConnected()
            TunAttachResult.Success
        } catch (e: CancellationException) {
            withContext(NonCancellable) { stopLocked() }
            throw e
        } catch (e: Exception) {
            PersistentLoggers.warn("OperaEngine", "TUN attach failed: ${e.javaClass.simpleName}")
            stopLocked()
            TunAttachResult.Failure("Opera TUN attachment failed")
        }
    }

    override suspend fun awaitReady(): EnginePlugin.ReadyResult =
        if (probe() is ProbeResult.Success) {
            EnginePlugin.ReadyResult.Ready
        } else {
            EnginePlugin.ReadyResult.Timeout("Opera route unavailable")
        }

    override suspend fun stop() = lifecycle.withLock { stopLocked() }

    private suspend fun stopLocked() {
        active = null
        currentStats.value = EngineStats()
        withContext(NonCancellable) {
            try {
                tun.stop()
            } finally {
                runtime.stop()
            }
        }
    }

    private fun markConnected() {
        currentStats.value = EngineStats(connectedSince = System.currentTimeMillis())
    }

    override suspend fun probe(): ProbeResult {
        val current = active ?: return ProbeResult.Failure("Opera not started")
        if (!current.config.proxyMode && (!current.attached || !tun.isRunning())) {
            return ProbeResult.Failure("Opera TUN runtime unavailable")
        }
        val result = runtime.probe()
        return if (active === current) result else ProbeResult.Failure("Opera session changed")
    }

    override fun stats(): Flow<EngineStats> = currentStats

    override suspend fun exitNodeStrategy(socksPort: Int): ExitNodeStrategy {
        val current = active
        return if (current != null && current.port == socksPort && (current.config.proxyMode || current.attached)) {
            ExitNodeStrategy.ViaSocks(OperaProxyCommand.LOOPBACK, socksPort)
        } else {
            ExitNodeStrategy.Unavailable("Opera SOCKS endpoint unavailable")
        }
    }

    override fun stopTimeoutMs(): Long = 6_000L
}
