package ru.ozero.enginescore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ChainOrchestratorStopCoverageTest {

    @Test
    fun `stop swallows engine stop exception and clears active list`() = runTest {
        val plugin = StopThrowingPlugin()
        val orchestrator = ChainOrchestrator(setOf(plugin))

        val start = orchestrator.start(listOf(ChainStep(plugin.id, EngineConfig.ByeDpi(socksPort = 1080))))
        assertIs<ChainResult.Success>(start)
        orchestrator.stop()

        assertEquals(1, plugin.stopCalls)
        assertEquals(emptyList(), orchestrator.activeEngines())
    }

    @Test
    fun `stop timeout clears active list and does not call engine twice`() = runTest {
        val plugin = SlowStopPlugin()
        val orchestrator = ChainOrchestrator(setOf(plugin))

        val start = orchestrator.start(listOf(ChainStep(plugin.id, EngineConfig.ByeDpi(socksPort = 1080))))
        assertIs<ChainResult.Success>(start)
        orchestrator.stop()
        orchestrator.stop()

        assertEquals(1, plugin.stopCalls)
        assertEquals(emptyList(), orchestrator.activeEngines())
    }

    @Test
    fun `start cancellation rolls back and rethrows cancellation`() = runTest {
        val plugin = CancellingStartPlugin()
        val orchestrator = ChainOrchestrator(setOf(plugin))

        assertFailsWith<CancellationException> {
            orchestrator.start(listOf(ChainStep(plugin.id, EngineConfig.ByeDpi(socksPort = 1080))))
        }

        assertEquals(emptyList(), orchestrator.activeEngines())
    }

    private class StopThrowingPlugin : EnginePlugin {
        override val id = EngineId.BYEDPI
        override val capabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = false,
            supportsDoH = false,
            localOnly = false,
            requiresServer = false,
            supportsUpstreamSocks = true,
        )
        var stopCalls = 0

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Success(1080)

        override suspend fun stop() {
            stopCalls++
            throw IllegalStateException("stop failed")
        }

        override suspend fun probe(): ProbeResult = ProbeResult.Failure("unused")
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
    }

    private class SlowStopPlugin : EnginePlugin {
        override val id = EngineId.BYEDPI
        override val capabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = false,
            supportsDoH = false,
            localOnly = false,
            requiresServer = false,
            supportsUpstreamSocks = true,
        )
        var stopCalls = 0

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Success(1080)

        override suspend fun stop() {
            stopCalls++
            delay(stopTimeoutMs() + 1)
        }

        override fun stopTimeoutMs(): Long = 1
        override suspend fun probe(): ProbeResult = ProbeResult.Failure("unused")
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
    }

    private class CancellingStartPlugin : EnginePlugin {
        override val id = EngineId.BYEDPI
        override val capabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = false,
            supportsDoH = false,
            localOnly = false,
            requiresServer = false,
            supportsUpstreamSocks = true,
        )

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            throw CancellationException("cancelled")

        override suspend fun stop() = Unit
        override suspend fun probe(): ProbeResult = ProbeResult.Failure("unused")
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
    }
}
