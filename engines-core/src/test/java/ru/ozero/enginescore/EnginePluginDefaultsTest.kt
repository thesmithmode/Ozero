package ru.ozero.enginescore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class EnginePluginDefaultsTest {

    @Test
    fun `default plugin methods return safe fallbacks`() = runTest {
        val plugin = FakePlugin()

        assertNull(plugin.tunSpec())
        assertNull(plugin.preflight())
        assertEquals(EnginePlugin.ReadyResult.Ready, plugin.awaitReady())
        assertEquals(EnginePlugin.PeerWatchdogPolicy(), plugin.peerWatchdogPolicy())
        assertEquals(EnginePlugin.DEFAULT_STOP_TIMEOUT_MS, plugin.stopTimeoutMs())
        assertNull(plugin.buildManualConfig(null))
        assertNull(plugin.buildProxyConfig(null))
        assertEquals("2 conns", plugin.statsLabel(EngineStats(activeConnections = 2)))
        assertNull(plugin.statsLabel(EngineStats(activeConnections = 0)))
        assertEquals(EnginePlugin.RecoverResult.NotSupported, plugin.recover())
    }

    @Test
    fun `default ipProbeRoute maps every exit node strategy`() = runTest {
        assertEquals(IpProbeRoute.Default, FakePlugin(ExitNodeStrategy.DirectHttp).ipProbeRoute(1080))
        assertEquals(IpProbeRoute.Socks("127.0.0.1", 1080), FakePlugin(ExitNodeStrategy.ViaSocks("127.0.0.1", 1080)).ipProbeRoute(1080))
        assertEquals(IpProbeRoute.StaticLocation("Germany", "DE"), FakePlugin(ExitNodeStrategy.LocationOnly("Germany", "DE")).ipProbeRoute(1080))
        assertEquals(
            IpProbeRoute.StaticLocation("Provider", "NL", "203.0.113.1"),
            FakePlugin(ExitNodeStrategy.ProviderLabel("Provider", "NL", "203.0.113.1")).ipProbeRoute(1080),
        )
        assertEquals(IpProbeRoute.AutoSelected, FakePlugin(ExitNodeStrategy.AutoSelected()).ipProbeRoute(1080))
        assertEquals(IpProbeRoute.Unavailable("offline"), FakePlugin(ExitNodeStrategy.Unavailable("offline")).ipProbeRoute(1080))
        assertIs<ExitNodeStrategy.Unavailable>(FakePlugin().exitNodeStrategy(1080))
    }

    private class FakePlugin(
        private val strategy: ExitNodeStrategy = ExitNodeStrategy.Unavailable("exit node strategy unavailable"),
    ) : EnginePlugin {
        override val id = EngineId.BYEDPI
        override val capabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = false,
            supportsDoH = false,
            localOnly = true,
            requiresServer = false,
            supportsUpstreamSocks = false,
        )

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult = StartResult.Success(1080)

        override suspend fun stop() = Unit

        override suspend fun probe(): ProbeResult = ProbeResult.Success(1)

        override fun stats(): Flow<EngineStats> = emptyFlow()

        override suspend fun exitNodeStrategy(socksPort: Int): ExitNodeStrategy = strategy
    }
}
