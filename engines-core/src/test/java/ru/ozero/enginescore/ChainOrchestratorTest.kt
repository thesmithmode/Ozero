package ru.ozero.enginescore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChainOrchestratorTest {

    @Test
    fun start_emptySteps_throws() = runTest {
        val orch = ChainOrchestrator(emptySet())
        assertFailsWith<IllegalArgumentException> { orch.start(emptyList()) }
    }

    @Test
    fun start_singleStep_returnsSuccessWithSocksPort() = runTest {
        val byedpi = FakePlugin(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)))
        val orch = ChainOrchestrator(setOf(byedpi))
        val r = orch.start(listOf(ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080))))
        assertIs<ChainResult.Success>(r)
        assertEquals(1080, r.finalSocksPort)
        assertEquals(1, byedpi.startCalls.size)
        assertEquals(Upstream.None, byedpi.startCalls[0].second)
    }

    @Test
    fun start_twoSteps_secondReceivesUpstreamFromFirst() = runTest {
        val byedpi = FakePlugin(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)))
        val fptn = FakePlugin(EngineId.FPTN, listOf(StartResult.Success(socksPort = 10808)))
        val orch = ChainOrchestrator(setOf(byedpi, fptn))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
                ChainStep(EngineId.FPTN, EngineConfig.Fptn()),
            ),
        )
        assertIs<ChainResult.Success>(r)
        assertEquals(10808, r.finalSocksPort)
        assertEquals(Upstream.None, byedpi.startCalls[0].second)
        assertEquals(Upstream.Socks5(host = "127.0.0.1", port = 1080), fptn.startCalls[0].second)
    }

    @Test
    fun start_engineNotInRegistry_returnsFailureRolledBack() = runTest {
        val byedpi = FakePlugin(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)))
        val orch = ChainOrchestrator(setOf(byedpi))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
                ChainStep(EngineId.FPTN, EngineConfig.Fptn()),
            ),
        )
        assertIs<ChainResult.Failure>(r)
        assertEquals(1, r.failedAtIndex)
        assertEquals(1, r.rolledBack)
        assertEquals(1, byedpi.stopCalls)
    }

    @Test
    fun start_secondStepFails_firstRolledBack() = runTest {
        val byedpi = FakePlugin(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)))
        val fptn = FakePlugin(EngineId.FPTN, listOf(StartResult.Failure(reason = "fptn no candidates")))
        val orch = ChainOrchestrator(setOf(byedpi, fptn))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
                ChainStep(EngineId.FPTN, EngineConfig.Fptn()),
            ),
        )
        assertIs<ChainResult.Failure>(r)
        assertEquals(1, r.failedAtIndex)
        assertEquals("fptn no candidates", r.reason)
        assertEquals(1, byedpi.stopCalls)
        assertEquals(0, fptn.stopCalls)
    }

    @Test
    fun start_engineThrows_returnsFailureWithMessage() = runTest {
        val a = FakePlugin(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)))
        val b = ThrowingPlugin(EngineId.FPTN, IllegalStateException("fptn boom"))
        val orch = ChainOrchestrator(setOf(a, b))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
                ChainStep(EngineId.FPTN, EngineConfig.Fptn()),
            ),
        )
        assertIs<ChainResult.Failure>(r)
        assertEquals(1, r.failedAtIndex)
        assertTrue(r.reason.contains("fptn boom"))
    }

    @Test
    fun stop_callsEnginesInReverseOrder() = runTest {
        val callOrder = mutableListOf<EngineId>()
        val byedpi = FakePlugin(
            EngineId.BYEDPI,
            listOf(StartResult.Success(socksPort = 1080)),
            onStop = { callOrder.add(EngineId.BYEDPI) },
        )
        val fptn = FakePlugin(
            EngineId.FPTN,
            listOf(StartResult.Success(socksPort = 10808)),
            onStop = { callOrder.add(EngineId.FPTN) },
        )
        val orch = ChainOrchestrator(setOf(byedpi, fptn))
        orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
                ChainStep(EngineId.FPTN, EngineConfig.Fptn()),
            ),
        )
        orch.stop()
        assertEquals(listOf(EngineId.FPTN, EngineId.BYEDPI), callOrder)
    }

    @Test
    fun start_secondStepNotSupportsUpstream_rejectedAndRolledBack() = runTest {
        val fptn = FakePlugin(
            EngineId.FPTN,
            listOf(StartResult.Success(socksPort = 10808)),
            supportsUpstreamSocks = true,
        )
        val byedpi = FakePlugin(
            EngineId.BYEDPI,
            listOf(StartResult.Success(socksPort = 1080)),
            supportsUpstreamSocks = false,
        )
        val orch = ChainOrchestrator(setOf(fptn, byedpi))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.FPTN, EngineConfig.Fptn()),
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
            ),
        )
        assertIs<ChainResult.Failure>(r)
        assertEquals(1, r.failedAtIndex)
        assertTrue(r.reason.contains("terminal-only"))
        assertEquals(0, byedpi.startCalls.size)
        assertEquals(1, fptn.stopCalls)
    }

    @Test
    fun start_supportsUpstreamSocks_secondStepAccepted() = runTest {
        val fptn1 = FakePlugin(
            EngineId.FPTN,
            listOf(StartResult.Success(socksPort = 10808)),
            supportsUpstreamSocks = true,
        )
        val fptn2 = FakePlugin(
            EngineId.SINGBOX,
            listOf(StartResult.Success(socksPort = 10809)),
            supportsUpstreamSocks = true,
        )
        val orch = ChainOrchestrator(setOf(fptn1, fptn2))
        val r = orch.start(
            listOf(
                ChainStep(EngineId.FPTN, EngineConfig.Fptn()),
                ChainStep(EngineId.SINGBOX, EngineConfig.Singbox(byteArrayOf(1), protocolType = 1)),
            ),
        )
        assertIs<ChainResult.Success>(r)
        assertEquals(Upstream.Socks5("127.0.0.1", 10808), fptn2.startCalls[0].second)
    }

    @Test
    fun start_multiStepRejectsEngineWithoutLocalSocksPort() = runTest {
        val warp = FakePlugin(
            EngineId.WARP,
            listOf(StartResult.Success(socksPort = 0)),
            supportsUpstreamSocks = false,
        )
        val fptn = FakePlugin(
            EngineId.FPTN,
            listOf(StartResult.Success(socksPort = 10808)),
            supportsUpstreamSocks = true,
        )
        val orch = ChainOrchestrator(setOf(warp, fptn))

        val r = orch.start(
            listOf(
                ChainStep(EngineId.WARP, EngineConfig.Warp),
                ChainStep(EngineId.FPTN, EngineConfig.Fptn()),
            ),
        )

        assertIs<ChainResult.Failure>(r)
        assertEquals(0, r.failedAtIndex)
        assertTrue(r.reason.contains("no local SOCKS port"))
        assertEquals(1, warp.stopCalls)
        assertEquals(0, fptn.startCalls.size)
    }

    @Test
    fun start_multiStepRejectsHeadWithoutStandaloneLocalSocksCapability() = runTest {
        val warp = FakePlugin(
            EngineId.WARP,
            listOf(StartResult.Success(socksPort = 0)),
            supportsUpstreamSocks = false,
            providesLocalSocks = false,
        )
        val fptn = FakePlugin(
            EngineId.FPTN,
            listOf(StartResult.Success(socksPort = 10808)),
            supportsUpstreamSocks = true,
        )
        val orch = ChainOrchestrator(setOf(warp, fptn))

        val r = orch.start(
            listOf(
                ChainStep(EngineId.WARP, EngineConfig.Warp),
                ChainStep(EngineId.FPTN, EngineConfig.Fptn()),
            ),
        )

        assertIs<ChainResult.Failure>(r)
        assertEquals(0, r.failedAtIndex)
        assertTrue(r.reason.contains("chain head"))
        assertEquals(0, warp.startCalls.size)
        assertEquals(0, warp.stopCalls)
        assertEquals(0, fptn.startCalls.size)
    }

    @Test
    fun start_multiStepRejectsHeadWithoutChainLocalSocksCapabilityAfterStart() = runTest {
        val head = FakePlugin(
            EngineId.BYEDPI,
            listOf(StartResult.Success(socksPort = 1080)),
            providesLocalSocks = false,
            providesLocalSocksWithoutUpstream = true,
        )
        val tail = FakePlugin(
            EngineId.FPTN,
            listOf(StartResult.Success(socksPort = 10808)),
            supportsUpstreamSocks = true,
        )
        val orch = ChainOrchestrator(setOf(head, tail))

        val r = orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
                ChainStep(EngineId.FPTN, EngineConfig.Fptn()),
            ),
        )

        assertIs<ChainResult.Failure>(r)
        assertEquals(0, r.failedAtIndex)
        assertTrue(r.reason.contains("no local SOCKS port"))
        assertEquals(0, head.startCalls.size)
        assertEquals(0, tail.startCalls.size)
    }

    @Test
    fun stop_canBeCalledTwice_withoutDoubleStop() = runTest {
        val byedpi = FakePlugin(EngineId.BYEDPI, listOf(StartResult.Success(socksPort = 1080)))
        val orch = ChainOrchestrator(setOf(byedpi))
        orch.start(listOf(ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080))))
        orch.stop()
        orch.stop()
        assertEquals(1, byedpi.stopCalls)
    }

    private class FakePlugin(
        override val id: EngineId,
        private val startResults: List<StartResult>,
        private val onStop: () -> Unit = {},
        supportsUpstreamSocks: Boolean = true,
        providesLocalSocks: Boolean = true,
        providesLocalSocksWithoutUpstream: Boolean = providesLocalSocks,
    ) : EnginePlugin {
        override val capabilities =
            EngineCapabilities(
                true,
                false,
                false,
                false,
                false,
                supportsUpstreamSocks,
                providesLocalSocks,
                providesLocalSocksWithoutUpstream,
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

    private class ThrowingPlugin(
        override val id: EngineId,
        private val toThrow: Throwable,
    ) : EnginePlugin {
        override val capabilities = EngineCapabilities(true, false, false, false, false, true)

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            throw toThrow

        override suspend fun stop() = Unit

        override suspend fun probe(): ProbeResult = ProbeResult.Failure("not used")

        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
    }
}
