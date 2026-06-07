package ru.ozero.enginescore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith

class ChainOrchestratorTimeoutAndCancellationTest {

    @Test
    fun `stop timeout keeps orchestrator usable for future starts`() = runTest {
        val slow = SlowStopPlugin(EngineId.BYEDPI)
        val next = SimplePlugin(EngineId.XRAY)
        val orch = ChainOrchestrator(setOf(slow, next))

        assertIs<ChainResult.Success>(
            orch.start(listOf(ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)))),
        )
        orch.stop()
        assertIs<ChainResult.Success>(
            orch.start(listOf(ChainStep(EngineId.XRAY, EngineConfig.Xray(configJson = "{}", socksPort = 10808)))),
        )
        assertEquals(1, next.startCount)
        assertEquals(1, slow.stopCount)
    }

    @Test
    fun `cancellation during stop is propagated`() = runTest {
        val plugin = CancellableStopPlugin(EngineId.BYEDPI)
        val orch = ChainOrchestrator(setOf(plugin))
        orch.start(listOf(ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080))))

        assertFailsWith<CancellationException> {
            orch.stop()
        }
    }

    private class SimplePlugin(
        override val id: EngineId,
    ) : EnginePlugin {
        override val capabilities = EngineCapabilities(true, false, false, false, false, true)
        var startCount = 0
        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
            startCount++
            return StartResult.Success(1080)
        }
        override suspend fun stop() {}
        override suspend fun probe(): ProbeResult = ProbeResult.Failure("n/a")
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
    }

    private class SlowStopPlugin(
        override val id: EngineId,
    ) : EnginePlugin {
        override val capabilities = EngineCapabilities(true, false, false, false, false, true)
        var stopCount = 0
        override fun stopTimeoutMs(): Long = 1
        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult = StartResult.Success(1080)
        override suspend fun stop() {
            stopCount++
            awaitCancellation()
        }
        override suspend fun probe(): ProbeResult = ProbeResult.Failure("n/a")
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
    }

    private class CancellableStopPlugin(
        override val id: EngineId,
    ) : EnginePlugin {
        override val capabilities = EngineCapabilities(true, false, false, false, false, true)
        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult = StartResult.Success(1080)
        override suspend fun stop() {
            throw CancellationException("cancelled")
        }
        override suspend fun probe(): ProbeResult = ProbeResult.Failure("n/a")
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
    }
}
