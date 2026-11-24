package ru.ozero.enginetgwss

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import ru.ozero.enginescore.settings.TrafficMode

class TelegramWssEngine : EnginePlugin {
    override val id = EngineId.TGWSS
    override val capabilities = EngineCapabilities(
        supportsTcp = true,
        supportsUdp = false,
        supportsDoH = false,
        localOnly = true,
        requiresServer = false,
        supportsUpstreamSocks = false,
    )

    private data class Session(val proxy: TelegramWssProxy, val port: Int, val startedAt: Long)

    private val mutex = Mutex()

    @Volatile
    private var session: Session? = null

    override fun buildProxyConfig(settings: SettingsModel?): EngineConfig? =
        if (settings?.trafficMode == TrafficMode.PROXY && settings.manualEngine == id) {
            EngineConfig.TelegramWss()
        } else {
            null
        }

    override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult = mutex.withLock {
        if (config !is EngineConfig.TelegramWss || upstream !is Upstream.None) {
            return@withLock StartResult.Failure("Telegram WSS requires standalone proxy mode")
        }
        closeSession()
        // Proxy-only mode never establishes a TUN; keep the operating system's normal socket routing.
        val proxy = TelegramWssProxy { true }
        try {
            val port = proxy.start(config.socksPort)
            session = Session(proxy, port, System.currentTimeMillis())
            StartResult.Success(port)
        } catch (failure: Exception) {
            proxy.close()
            StartResult.Failure("Telegram WSS listener failed", failure)
        }
    }

    override suspend fun stop() = mutex.withLock { closeSession() }

    private fun closeSession() {
        val previous = session
        session = null
        previous?.proxy?.close()
    }

    override suspend fun probe(): ProbeResult {
        val current = session ?: return ProbeResult.Failure("Telegram WSS is not running")
        return try {
            ProbeResult.Success(Socks5HandshakeProbe.probe("127.0.0.1", current.port))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            ProbeResult.Failure("Telegram WSS listener unavailable", failure)
        }
    }

    override suspend fun awaitReady(): EnginePlugin.ReadyResult = when (val result = probe()) {
        is ProbeResult.Success -> EnginePlugin.ReadyResult.Ready
        is ProbeResult.Failure -> EnginePlugin.ReadyResult.Timeout(result.reason)
    }

    override suspend fun exitNodeStrategy(socksPort: Int): ExitNodeStrategy =
        ExitNodeStrategy.Unavailable("Telegram-only proxy has no general internet exit")

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
