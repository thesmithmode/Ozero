package ru.ozero.enginetgwss

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import ru.ozero.enginescore.probe.Socks5HandshakeProbe
import ru.ozero.enginescore.settings.SettingsModel
import java.util.concurrent.TimeUnit

class TelegramWssEngine internal constructor(
    private val warp: EnginePlugin,
    private val newProxy: (Int, Boolean) -> TelegramWssProxy,
    private val probeWarp: suspend (Int) -> Boolean,
) : EnginePlugin {
    constructor(context: Context, warp: EnginePlugin) : this(
        warp,
        { port, tunnelMode ->
            val network = TelegramNetwork(context)
            TelegramWssProxy(port, network.dns) { socket -> !tunnelMode || network.protect(socket) }
        },
        { port -> runInterruptible(Dispatchers.IO) { WarpRouteProbe.check(port) } },
    )

    override val id = EngineId.TGWSS
    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = true,
        supportsDoH = false,
        localOnly = false,
        requiresServer = false,
        supportsUpstreamSocks = false,
    )

    private data class Session(
        val proxy: TelegramWssProxy,
        val port: Int,
        val startedAt: Long,
    )

    private val mutex = Mutex()

    @Volatile
    private var session: Session? = null

    override fun buildManualConfig(settings: SettingsModel?): EngineConfig = EngineConfig.TelegramWss()

    override fun buildProxyConfig(settings: SettingsModel?): EngineConfig =
        EngineConfig.TelegramWss(tunnelMode = false)

    override fun stopTimeoutMs(): Long = 3_500L

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult = mutex.withLock {
        if (config !is EngineConfig.TelegramWss || upstream !is Upstream.None) {
            return@withLock StartResult.Failure("Telegram WSS requires standalone mode")
        }
        closeSession()
        var proxy: TelegramWssProxy? = null
        var started = false
        try {
            val fallback = warp.start(EngineConfig.WarpProxy(), Upstream.None)
            if (fallback !is StartResult.Success || fallback.socksPort <= 0) {
                return@withLock StartResult.Failure("WARP fallback could not start")
            }
            if (warp.awaitReady() != EnginePlugin.ReadyResult.Ready) {
                return@withLock StartResult.Failure("WARP fallback not ready")
            }
            proxy = newProxy(fallback.socksPort, config.tunnelMode)
            val port = proxy.start(config.socksPort)
            session = Session(proxy, port, System.currentTimeMillis())
            started = true
            StartResult.Success(port)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            StartResult.Failure("Telegram WSS start failed", failure)
        } finally {
            if (!started) {
                try {
                    proxy?.close()
                } finally {
                    withContext(NonCancellable) { warp.stop() }
                }
            }
        }
    }

    override suspend fun stop() = mutex.withLock { closeSession() }

    private suspend fun closeSession() {
        val previous = session ?: return
        session = null
        try {
            previous.proxy.close()
        } finally {
            withContext(NonCancellable) { warp.stop() }
        }
    }

    override suspend fun probe(): ProbeResult {
        val current = session ?: return ProbeResult.Failure("Telegram WSS is not running")
        return try {
            val started = System.nanoTime()
            Socks5HandshakeProbe.probe("127.0.0.1", current.port)
            if (!probeWarp(current.port) || session !== current) {
                ProbeResult.Failure("WARP TCP/UDP fallback unavailable")
            } else {
                ProbeResult.Success(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ProbeResult.Failure("Telegram WSS unavailable", failure)
        }
    }

    override suspend fun awaitReady(): EnginePlugin.ReadyResult {
        val current = session ?: return EnginePlugin.ReadyResult.Timeout("Telegram WSS is not running")
        val result = probe()
        if (result is ProbeResult.Failure) return EnginePlugin.ReadyResult.Timeout(result.reason)
        val wss = runInterruptible(Dispatchers.IO) { current.proxy.probeRemote() }
        return if (wss != null && session === current) {
            EnginePlugin.ReadyResult.Ready
        } else {
            EnginePlugin.ReadyResult.Timeout("Telegram WSS endpoint unavailable")
        }
    }

    override suspend fun exitNodeStrategy(socksPort: Int): ExitNodeStrategy =
        session?.let { ExitNodeStrategy.ViaSocks("127.0.0.1", it.port) }
            ?: ExitNodeStrategy.Unavailable("Telegram WSS is not running")

    override fun stats(): Flow<EngineStats> = flow {
        while (true) {
            val current = session
            emit(
                if (current == null) {
                    EngineStats()
                } else {
                    EngineStats(
                        bytesIn = current.proxy.bytesIn,
                        bytesOut = current.proxy.bytesOut,
                        connectedSince = current.startedAt,
                        activeConnections = current.proxy.activeConnections,
                    )
                },
            )
            delay(1000)
        }
    }
}
