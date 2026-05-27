package ru.ozero.enginescore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChainOrchestratorProxyChainTest {

    @Test
    fun `byedpi head + singbox tail - singbox receives upstream from byedpi`() = runTest {
        val byedpi = FakePlugin(
            EngineId.BYEDPI,
            listOf(StartResult.Success(socksPort = 49152)),
            supportsUpstreamSocks = false,
        )
        val singbox = FakePlugin(
            EngineId.SINGBOX,
            listOf(StartResult.Success(socksPort = 49408)),
            supportsUpstreamSocks = true,
        )
        val orch = ChainOrchestrator(setOf(byedpi, singbox))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 49152)),
                ChainStep(
                    EngineId.SINGBOX,
                    EngineConfig.Singbox(beanBlob = ByteArray(0), protocolType = 0),
                ),
            ),
        )
        assertIs<ChainResult.Success>(r)
        assertEquals(49408, r.finalSocksPort)
        assertEquals(Upstream.None, byedpi.startCalls[0].second)
        assertEquals(
            Upstream.Socks5("127.0.0.1", 49152),
            singbox.startCalls[0].second,
        )
    }

    @Test
    fun `byedpi cannot be tail - upstream rejected`() = runTest {
        val singbox = FakePlugin(
            EngineId.SINGBOX,
            listOf(StartResult.Success(socksPort = 49408)),
            supportsUpstreamSocks = true,
        )
        val byedpi = FakePlugin(
            EngineId.BYEDPI,
            listOf(StartResult.Success(socksPort = 49152)),
            supportsUpstreamSocks = false,
        )
        val orch = ChainOrchestrator(setOf(singbox, byedpi))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.SINGBOX, EngineConfig.Singbox(ByteArray(0), 0)),
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 49152)),
            ),
        )
        assertIs<ChainResult.Failure>(r)
        assertEquals(1, r.failedAtIndex)
    }

    @Test
    fun `three step chain - each step gets correct upstream`() = runTest {
        val a = FakePlugin(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 100)))
        val b = FakePlugin(
            EngineId.MASTERDNS,
            listOf(StartResult.Success(socksPort = 200)),
            supportsUpstreamSocks = true,
        )
        val c = FakePlugin(
            EngineId.SINGBOX,
            listOf(StartResult.Success(socksPort = 300)),
            supportsUpstreamSocks = true,
        )
        val orch = ChainOrchestrator(setOf(a, b, c))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 100)),
                ChainStep(EngineId.MASTERDNS, EngineConfig.MasterDns("", emptyList(), 200)),
                ChainStep(EngineId.SINGBOX, EngineConfig.Singbox(ByteArray(0), 0)),
            ),
        )
        assertIs<ChainResult.Success>(r)
        assertEquals(300, r.finalSocksPort)
        assertEquals(Upstream.None, a.startCalls[0].second)
        assertEquals(Upstream.Socks5("127.0.0.1", 100), b.startCalls[0].second)
        assertEquals(Upstream.Socks5("127.0.0.1", 200), c.startCalls[0].second)
    }

    @Test
    fun `singbox WireGuard config carried through chain step`() = runTest {
        val wg = WireGuardOutboundConfig(
            privateKey = "pk=",
            peerPublicKey = "pub=",
            serverHost = "1.2.3.4",
            serverPort = 51820,
            localAddresses = listOf("10.0.0.1/32"),
        )
        val singbox = FakePlugin(
            EngineId.SINGBOX,
            listOf(StartResult.Success(socksPort = 49420)),
            supportsUpstreamSocks = true,
        )
        val byedpi = FakePlugin(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 49152)))
        val orch = ChainOrchestrator(setOf(byedpi, singbox))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 49152)),
                ChainStep(EngineId.SINGBOX, EngineConfig.Singbox(ByteArray(0), 0, wireGuardConfig = wg)),
            ),
        )
        assertIs<ChainResult.Success>(r)
        val passedConfig = singbox.startCalls[0].first as EngineConfig.Singbox
        assertEquals(wg, passedConfig.wireGuardConfig)
    }

    private class FakePlugin(
        override val id: EngineId,
        private val startResults: List<StartResult>,
        supportsUpstreamSocks: Boolean = true,
    ) : EnginePlugin {
        override val capabilities =
            EngineCapabilities(true, false, false, false, false, supportsUpstreamSocks)
        val startCalls = mutableListOf<Pair<EngineConfig, Upstream>>()
        var stopCalls = 0
            private set

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
            startCalls.add(config to upstream)
            return startResults[startCalls.size - 1]
        }

        override suspend fun stop() {
            stopCalls++
        }
        override suspend fun probe(): ProbeResult = ProbeResult.Failure("unused")
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
    }
}
