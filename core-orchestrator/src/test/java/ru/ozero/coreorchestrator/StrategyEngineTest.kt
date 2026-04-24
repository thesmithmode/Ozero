package ru.ozero.coreorchestrator

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.ProbeResult
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StrategyEngineTest {
    private fun mockEngine(probeResult: ProbeResult): Engine =
        mockk<Engine> { coEvery { probe() } returns probeResult }

    @Test
    fun buildCandidatesReturnsByeDpi() {
        val strategy = StrategyEngine(emptyMap())
        val candidates = strategy.buildCandidates()
        assertEquals(1, candidates.size)
        assertEquals(EngineId.BYEDPI, candidates[0].engineId)
        assertEquals(EngineConfig.ByeDpi(), candidates[0].config)
    }

    @Test
    fun pickBestReturnsFirstSuccessfulCandidate() = runTest {
        val byeDpiEngine = mockEngine(ProbeResult.Success(latencyMs = 50L))
        val strategy = StrategyEngine(mapOf(EngineId.BYEDPI to byeDpiEngine))
        val candidates = strategy.buildCandidates()
        val result = strategy.pickBest(candidates)
        assertNotNull(result)
        assertEquals(EngineId.BYEDPI, result.engineId)
    }

    @Test
    fun pickBestReturnsNullWhenAllProbesFail() = runTest {
        val byeDpiEngine = mockEngine(ProbeResult.Failure(reason = "timeout"))
        val strategy = StrategyEngine(mapOf(EngineId.BYEDPI to byeDpiEngine))
        val candidates = strategy.buildCandidates()
        val result = strategy.pickBest(candidates)
        assertNull(result)
    }

    @Test
    fun pickBestSkipsMissingEngines() = runTest {
        val strategy = StrategyEngine(emptyMap())
        val candidates = listOf(Candidate(EngineId.BYEDPI, EngineConfig.ByeDpi()))
        val result = strategy.pickBest(candidates)
        assertNull(result)
    }

    @Test
    fun pickBestReturnsEmptyListResult() = runTest {
        val strategy = StrategyEngine(emptyMap())
        val result = strategy.pickBest(emptyList())
        assertNull(result)
    }
}
