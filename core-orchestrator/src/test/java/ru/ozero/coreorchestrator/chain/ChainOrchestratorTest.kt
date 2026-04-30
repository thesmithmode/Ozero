package ru.ozero.coreorchestrator.chain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineCapabilities
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.EngineStats
import ru.ozero.coreapi.ProbeResult
import ru.ozero.coreapi.StartResult
import ru.ozero.coreapi.Upstream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChainOrchestratorTest {

    @Test
    fun start_emptySteps_throws() = runTest {
        val orch = ChainOrchestrator(emptyMap())
        assertFailsWith<IllegalArgumentException> { orch.start(emptyList()) }
    }

    @Test
    fun start_singleStep_returnsSuccessWithSocksPort() = runTest {
        val byedpi = FakeEngine(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)))
        val orch = ChainOrchestrator(mapOf(EngineId.BYEDPI to byedpi))
        val r = orch.start(listOf(ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080))))
        assertIs<ChainResult.Success>(r)
        assertEquals(1080, r.finalSocksPort)
        assertEquals(1, byedpi.startCalls.size)
        assertEquals(Upstream.None, byedpi.startCalls[0].second)
    }

    @Test
    fun start_twoSteps_secondReceivesUpstreamFromFirst() = runTest {
        val byedpi = FakeEngine(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)))
        val xray = FakeEngine(EngineId.XRAY, listOf(StartResult.Success(socksPort = 10808)))
        val orch = ChainOrchestrator(mapOf(EngineId.BYEDPI to byedpi, EngineId.XRAY to xray))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
                ChainStep(EngineId.XRAY, EngineConfig.Xray(configJson = "{}", socksPort = 10808)),
            ),
        )
        assertIs<ChainResult.Success>(r)
        assertEquals(10808, r.finalSocksPort)
        assertEquals(Upstream.None, byedpi.startCalls[0].second)
        assertEquals(Upstream.Socks5(host = "127.0.0.1", port = 1080), xray.startCalls[0].second)
    }

    @Test
    fun start_threeSteps_thirdGetsUpstreamFromSecond() = runTest {
        val a = FakeEngine(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)))
        val b = FakeEngine(EngineId.XRAY, listOf(StartResult.Success(socksPort = 10808)))
        val c = FakeEngine(EngineId.NAIVE, listOf(StartResult.Success(socksPort = 1090)))
        val orch = ChainOrchestrator(
            mapOf(
                EngineId.BYEDPI to a,
                EngineId.XRAY to b,
                EngineId.NAIVE to c,
            ),
        )
        val r = orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
                ChainStep(EngineId.XRAY, EngineConfig.Xray("{}", socksPort = 10808)),
                ChainStep(EngineId.NAIVE, EngineConfig.Naive("http://x", socksPort = 1090)),
            ),
        )
        assertIs<ChainResult.Success>(r)
        assertEquals(1090, r.finalSocksPort)
        assertEquals(Upstream.Socks5("127.0.0.1", 10808), c.startCalls[0].second)
    }

    @Test
    fun start_engineNotInRegistry_returnsFailureRolledBack() = runTest {
        val byedpi = FakeEngine(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)))
        val orch = ChainOrchestrator(mapOf(EngineId.BYEDPI to byedpi))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
                ChainStep(EngineId.XRAY, EngineConfig.Xray("{}", socksPort = 10808)),
            ),
        )
        assertIs<ChainResult.Failure>(r)
        assertEquals(1, r.failedAtIndex)
        assertEquals(1, r.rolledBack)
        assertEquals(1, byedpi.stopCalls)
    }

    @Test
    fun start_secondStepFails_firstRolledBack() = runTest {
        val byedpi = FakeEngine(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)))
        val xray = FakeEngine(EngineId.XRAY, listOf(StartResult.Failure(reason = "xray no candidates")))
        val orch = ChainOrchestrator(mapOf(EngineId.BYEDPI to byedpi, EngineId.XRAY to xray))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
                ChainStep(EngineId.XRAY, EngineConfig.Xray("{}", socksPort = 10808)),
            ),
        )
        assertIs<ChainResult.Failure>(r)
        assertEquals(1, r.failedAtIndex)
        assertEquals("xray no candidates", r.reason)
        assertEquals(1, byedpi.stopCalls)
        assertEquals(0, xray.stopCalls)
    }

    @Test
    fun start_engineThrows_returnsFailureWithMessage() = runTest {
        val a = FakeEngine(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)))
        val b = ThrowingEngine(EngineId.XRAY, IllegalStateException("xray boom"))
        val orch = ChainOrchestrator(mapOf(EngineId.BYEDPI to a, EngineId.XRAY to b))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
                ChainStep(EngineId.XRAY, EngineConfig.Xray("{}", socksPort = 10808)),
            ),
        )
        assertIs<ChainResult.Failure>(r)
        assertEquals(1, r.failedAtIndex)
        assertTrue(r.reason.contains("xray boom"), "reason должен содержать сообщение исключения: ${r.reason}")
    }

    @Test
    fun stop_callsEnginesInReverseOrder() = runTest {
        val callOrder = mutableListOf<EngineId>()
        val byedpi = FakeEngine(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)), onStop = {
            callOrder.add(EngineId.BYEDPI)
        })
        val xray = FakeEngine(EngineId.XRAY, listOf(StartResult.Success(socksPort = 10808)), onStop = {
            callOrder.add(EngineId.XRAY)
        })
        val orch = ChainOrchestrator(mapOf(EngineId.BYEDPI to byedpi, EngineId.XRAY to xray))
        orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
                ChainStep(EngineId.XRAY, EngineConfig.Xray("{}", socksPort = 10808)),
            ),
        )
        orch.stop()
        assertEquals(listOf(EngineId.XRAY, EngineId.BYEDPI), callOrder)
    }

    @Test
    fun start_engineThrowsCancellationException_propagatesAndRollsBack() = runTest {
        val byedpi = FakeEngine(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)))
        val xray = ThrowingEngine(EngineId.XRAY, kotlinx.coroutines.CancellationException("scope cancelled"))
        val orch = ChainOrchestrator(mapOf(EngineId.BYEDPI to byedpi, EngineId.XRAY to xray))
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            orch.start(
                listOf(
                    ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
                    ChainStep(EngineId.XRAY, EngineConfig.Xray("{}", socksPort = 10808)),
                ),
            )
        }
        assertEquals(1, byedpi.stopCalls, "при CE первый engine должен откатиться")
    }

    @Test
    fun start_secondStepNotSupportsUpstream_rejectedAndRolledBack() = runTest {
        val xray = FakeEngine(
            EngineId.XRAY,
            listOf(StartResult.Success(socksPort = 10808)),
            supportsUpstreamSocks = true,
        )
        val byedpi = FakeEngine(
            EngineId.BYEDPI,
            listOf(StartResult.Success(socksPort = 1080)),
            supportsUpstreamSocks = false,
        )
        val orch = ChainOrchestrator(mapOf(EngineId.XRAY to xray, EngineId.BYEDPI to byedpi))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.XRAY, EngineConfig.Xray("{}", socksPort = 10808)),
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
            ),
        )
        assertIs<ChainResult.Failure>(r)
        assertEquals(1, r.failedAtIndex)
        assertTrue(r.reason.contains("terminal-only"), "reason должен указывать причину: ${r.reason}")
        assertEquals(0, byedpi.startCalls.size, "byedpi.start не должен вызваться")
        assertEquals(1, xray.stopCalls, "xray должен откатиться")
    }

    @Test
    fun start_supportsUpstreamSocks_secondStepAccepted() = runTest {
        val xray1 = FakeEngine(
            EngineId.XRAY,
            listOf(StartResult.Success(socksPort = 10808)),
            supportsUpstreamSocks = true,
        )
        val xray2 = FakeEngine(
            EngineId.HYSTERIA2,
            listOf(StartResult.Success(socksPort = 10809)),
            supportsUpstreamSocks = true,
        )
        val orch = ChainOrchestrator(mapOf(EngineId.XRAY to xray1, EngineId.HYSTERIA2 to xray2))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.XRAY, EngineConfig.Xray("{}", socksPort = 10808)),
                ChainStep(EngineId.HYSTERIA2, EngineConfig.Hysteria2("{}", socksPort = 10809)),
            ),
        )
        assertIs<ChainResult.Success>(r)
        assertEquals(Upstream.Socks5("127.0.0.1", 10808), xray2.startCalls[0].second)
    }

    @Test
    fun stop_canBeCalledTwice_withoutDoubleStop() = runTest {
        val byedpi = FakeEngine(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)))
        val orch = ChainOrchestrator(mapOf(EngineId.BYEDPI to byedpi))
        orch.start(listOf(ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080))))
        orch.stop()
        orch.stop()
        assertEquals(1, byedpi.stopCalls, "повторный stop не должен снова вызывать engine.stop")
    }

    private class FakeEngine(
        override val id: EngineId,
        private val startResults: List<StartResult>,
        private val onStop: () -> Unit = {},
        supportsUpstreamSocks: Boolean = true,
    ) : Engine {
        override val capabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = false,
            supportsDoH = false,
            localOnly = false,
            requiresServer = false,
            supportsUpstreamSocks = supportsUpstreamSocks,
        )
        val startCalls = mutableListOf<Pair<EngineConfig, Upstream>>()
        var stopCalls = 0
            private set

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
            startCalls.add(config to upstream)
            return startResults[startCalls.size - 1]
        }
        override suspend fun stop() {
            stopCalls++
            onStop()
        }
        override suspend fun probe(): ProbeResult = ProbeResult.Failure("not used")
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
    }

    private class ThrowingEngine(
        override val id: EngineId,
        private val toThrow: Throwable,
    ) : Engine {
        override val capabilities = EngineCapabilities(
            supportsTcp = true, supportsUdp = false, supportsDoH = false,
            localOnly = false, requiresServer = false,
        )
        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult = throw toThrow
        override suspend fun stop() = Unit
        override suspend fun probe(): ProbeResult = ProbeResult.Failure("not used")
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
    }
}
