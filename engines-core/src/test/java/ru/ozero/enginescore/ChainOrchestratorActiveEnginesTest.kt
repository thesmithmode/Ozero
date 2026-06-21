package ru.ozero.enginescore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChainOrchestratorActiveEnginesTest {

    @Test
    fun activeEngines_initiallyEmpty() {
        val orch = ChainOrchestrator(setOf(plugin(EngineId.BYEDPI)))
        assertTrue(orch.activeEngines().isEmpty())
    }

    @Test
    fun activeEngines_afterStart_containsStartedPlugin() = runTest {
        val byedpi = plugin(EngineId.BYEDPI)
        val orch = ChainOrchestrator(setOf(byedpi))
        orch.start(listOf(ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080))))
        val active = orch.activeEngines()
        assertEquals(1, active.size)
        assertEquals(EngineId.BYEDPI, active[0].id)
    }

    @Test
    fun activeEngines_afterStop_isEmpty() = runTest {
        val byedpi = plugin(EngineId.BYEDPI)
        val orch = ChainOrchestrator(setOf(byedpi))
        orch.start(listOf(ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080))))
        orch.stop()
        assertTrue(orch.activeEngines().isEmpty())
    }

    @Test
    fun activeEngines_returnsSnapshotNotLiveView() = runTest {
        val byedpi = plugin(EngineId.BYEDPI)
        val orch = ChainOrchestrator(setOf(byedpi))
        orch.start(listOf(ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080))))
        val snapshot = orch.activeEngines()
        orch.stop()
        assertEquals(1, snapshot.size, "snapshot не должен меняться при последующем stop()")
    }

    @Test
    fun activeEngines_orderMatchesStartOrder() = runTest {
        val byedpi = plugin(EngineId.BYEDPI)
        val fptn = plugin(EngineId.FPTN)
        val orch = ChainOrchestrator(setOf(byedpi, fptn))
        orch.start(
            listOf(
                ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)),
                ChainStep(EngineId.FPTN, EngineConfig.Fptn()),
            ),
        )
        val active = orch.activeEngines()
        assertEquals(EngineId.BYEDPI, active[0].id)
        assertEquals(EngineId.FPTN, active[1].id)
    }

    private fun plugin(engineId: EngineId): EnginePlugin = object : EnginePlugin {
        override val id = engineId
        override val capabilities =
            EngineCapabilities(true, false, false, false, false, true)
        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Success(socksPort = 1080)
        override suspend fun stop() = Unit
        override suspend fun probe(): ProbeResult = ProbeResult.Failure("not used")
        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
    }
}
