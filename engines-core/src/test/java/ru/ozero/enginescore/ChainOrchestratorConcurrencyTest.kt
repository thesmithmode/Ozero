package ru.ozero.enginescore

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChainOrchestratorConcurrencyTest {

    @Test
    fun parallelStartAndStop_noExceptions() = runTest(StandardTestDispatcher()) {
        val plugin = CountingPlugin(EngineId.BYEDPI)
        val orch = ChainOrchestrator(setOf(plugin))

        val jobs = (1..10).map { idx ->
            if (idx % 2 == 0) {
                launch {
                    orch.start(
                        listOf(ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)))
                    )
                }
            } else {
                launch {
                    orch.stop()
                }
            }
        }

        jobs.forEach { it.join() }
        assertEquals(
            5,
            plugin.startCount,
            "5 parallel starts must execute sequentially",
        )
        assertEquals(
            4,
            plugin.stopCount,
            "first stop hits empty chain — no-op; 4 remaining stops each halt running engine",
        )
    }

    @Test
    fun parallelStarts_onlyLastOneSucceeds_othersLocked() = runTest(StandardTestDispatcher()) {
        val plugin = CountingPlugin(EngineId.BYEDPI)
        val orch = ChainOrchestrator(setOf(plugin))

        val jobs = (1..5).map {
            launch {
                orch.start(
                    listOf(ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080)))
                )
            }
        }

        jobs.forEach { it.join() }
        assertEquals(
            plugin.startCount,
            5,
            "all 5 concurrent starts should execute sequentially due to mutex"
        )
    }

    @Test
    fun startThenConcurrentStops_allSafe() = runTest(StandardTestDispatcher()) {
        val plugin = CountingPlugin(EngineId.BYEDPI)
        val orch = ChainOrchestrator(setOf(plugin))

        orch.start(listOf(ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080))))

        val stopJobs = (1..3).map {
            launch {
                orch.stop()
            }
        }

        stopJobs.forEach { it.join() }
        assertEquals(1, plugin.stopCount, "stop should execute safely, engines only stopped once due to mutex")
    }

    @Test
    fun stopTimeout_releasesMutexForNextEngineStart() = runTest(StandardTestDispatcher()) {
        val wedgedByeDpi = WedgedStopPlugin(EngineId.BYEDPI)
        val nextEngine = CountingPlugin(EngineId.WARP)
        val orch = ChainOrchestrator(setOf(wedgedByeDpi, nextEngine))

        orch.start(listOf(ChainStep(EngineId.BYEDPI, EngineConfig.ByeDpi(socksPort = 1080))))
        orch.stop()

        val result = orch.start(listOf(ChainStep(EngineId.WARP, EngineConfig.Warp)))

        assertIs<ChainResult.Success>(result)
        assertEquals(1, nextEngine.startCount)
        assertEquals(1, wedgedByeDpi.stopCount)
    }

    private class CountingPlugin(
        override val id: EngineId,
    ) : EnginePlugin {
        override val capabilities =
            EngineCapabilities(true, false, false, false, false, true)

        var startCount = 0
        var stopCount = 0

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
            startCount++
            return StartResult.Success(socksPort = 1080)
        }

        override suspend fun stop() {
            stopCount++
        }

        override suspend fun probe(): ProbeResult = ProbeResult.Failure("not used")

        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
    }

    private class WedgedStopPlugin(
        override val id: EngineId,
    ) : EnginePlugin {
        override val capabilities =
            EngineCapabilities(true, false, false, false, false, true)

        var stopCount = 0

        override fun stopTimeoutMs(): Long = 1L

        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Success(socksPort = 1080)

        override suspend fun stop() {
            stopCount++
            awaitCancellation()
        }

        override suspend fun probe(): ProbeResult = ProbeResult.Failure("not used")

        override fun stats(): Flow<EngineStats> = flowOf(EngineStats())
    }
}
