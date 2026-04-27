package ru.ozero.coreorchestrator

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.coreapi.Engine
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.ProbeResult
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrategyEngineTest {
    private fun mockEngine(probeResult: ProbeResult, delayMs: Long = 0L): Engine =
        mockk<Engine> {
            coEvery { probe() } coAnswers {
                if (delayMs > 0) delay(delayMs)
                probeResult
            }
        }

    private fun xrayCandidate(priority: Int, port: Int = 10808 + priority): Candidate =
        Candidate(
            engineId = EngineId.XRAY,
            config = EngineConfig.Xray(configJson = "{}", socksPort = port),
            priority = priority,
        )

    @Test
    fun buildCandidatesContainsByeDpiByDefault() = runTest {
        val strategy = StrategyEngine(emptyMap())
        val candidates = strategy.buildCandidates()
        assertEquals(1, candidates.size)
        assertEquals(EngineId.BYEDPI, candidates[0].engineId)
        assertEquals(Candidate.PRIORITY_BYEDPI, candidates[0].priority)
    }

    @Test
    fun buildCandidatesSortsByPriorityDescending() = runTest {
        val source = CandidateSource {
            listOf(
                xrayCandidate(Candidate.PRIORITY_XRAY_SHADOWSOCKS),
                xrayCandidate(Candidate.PRIORITY_XRAY_VLESS_REALITY),
                xrayCandidate(Candidate.PRIORITY_XRAY_HYSTERIA2),
            )
        }
        val strategy = StrategyEngine(emptyMap(), extraSources = listOf(source))
        val candidates = strategy.buildCandidates()

                assertEquals(
            listOf(
                Candidate.PRIORITY_XRAY_VLESS_REALITY,
                Candidate.PRIORITY_XRAY_HYSTERIA2,
                Candidate.PRIORITY_XRAY_SHADOWSOCKS,
                Candidate.PRIORITY_BYEDPI,
            ),
            candidates.map { it.priority },
        )
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
    fun pickBestPrefersHigherPriorityEvenIfSlower() = runTest {
                        val xrayEngine = mockEngine(ProbeResult.Success(latencyMs = 200L), delayMs = 30L)
        val byeDpiEngine = mockEngine(ProbeResult.Success(latencyMs = 10L), delayMs = 5L)
        val strategy = StrategyEngine(
            mapOf(EngineId.XRAY to xrayEngine, EngineId.BYEDPI to byeDpiEngine),
            extraSources = listOf(CandidateSource { listOf(xrayCandidate(Candidate.PRIORITY_XRAY_VLESS_REALITY)) }),
        )
        val candidates = strategy.buildCandidates()
        val result = strategy.pickBest(candidates)

        assertNotNull(result)
        assertEquals(EngineId.XRAY, result.engineId)
    }

    @Test
    fun pickBestProbesOnlyTopThreeInParallel() = runTest {
        val highEngine = mockEngine(ProbeResult.Failure("HIGH"))
        val midEngine = mockEngine(ProbeResult.Failure("MID"))
        val lowEngine = mockEngine(ProbeResult.Success(latencyMs = 10L))
        val byeDpiEngine = mockEngine(ProbeResult.Success(latencyMs = 5L))

                val strategy = StrategyEngine(
            engines = mapOf(
                EngineId.XRAY to highEngine,
                EngineId.AMNEZIA to midEngine,
                EngineId.NAIVE to lowEngine,
                EngineId.BYEDPI to byeDpiEngine,
            ),
            extraSources = listOf(
                CandidateSource {
                    listOf(
                        xrayCandidate(Candidate.PRIORITY_XRAY_VLESS_REALITY),
                        Candidate(EngineId.AMNEZIA, EngineConfig.Amnezia("{}", 10809), Candidate.PRIORITY_AMNEZIA),
                        Candidate(EngineId.NAIVE, EngineConfig.Naive("naive://x", 1080), 6),
                    )
                },
            ),
        )
        val candidates = strategy.buildCandidates()
        val result = strategy.pickBest(candidates)

        assertNotNull(result)
                assertEquals(EngineId.NAIVE, result.engineId)
                coVerify(exactly = 0) { byeDpiEngine.probe() }
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
    fun pickBestReturnsNullForEmptyList() = runTest {
        val strategy = StrategyEngine(emptyMap())
        val result = strategy.pickBest(emptyList())
        assertNull(result)
    }

    
    @Test
    fun udpReachableTrueKeepsHysteria2Candidate() = runTest {
        val hy2 = Candidate(
            engineId = EngineId.HYSTERIA2,
            config = EngineConfig.ByeDpi(),
            priority = Candidate.PRIORITY_HYSTERIA2_NATIVE,
            requiresUdp = true,
        )
        val strategy = StrategyEngine(
            engines = emptyMap(),
            extraSources = listOf(CandidateSource { listOf(hy2) }),
            udpReachable = { true },
        )
        val list = strategy.buildCandidates()
        assertEquals(EngineId.HYSTERIA2, list[0].engineId)
    }

    @Test
    fun udpReachableFalseFiltersHysteria2() = runTest {
        val hy2 = Candidate(
            engineId = EngineId.HYSTERIA2,
            config = EngineConfig.ByeDpi(),
            priority = Candidate.PRIORITY_HYSTERIA2_NATIVE,
            requiresUdp = true,
        )
        val vless = xrayCandidate(Candidate.PRIORITY_XRAY_VLESS_REALITY)
        val strategy = StrategyEngine(
            engines = emptyMap(),
            extraSources = listOf(CandidateSource { listOf(hy2, vless) }),
            udpReachable = { false },
        )
        val list = strategy.buildCandidates()
        assertTrue(list.none { it.engineId == EngineId.HYSTERIA2 }, "Hy2 не отфильтрован при CGNAT")
                assertEquals(EngineId.XRAY, list[0].engineId)
    }

    @Test
    fun udpReachableFalseKeepsTcpCandidates() = runTest {
        val vless = xrayCandidate(Candidate.PRIORITY_XRAY_VLESS_REALITY)
        val strategy = StrategyEngine(
            engines = emptyMap(),
            extraSources = listOf(CandidateSource { listOf(vless) }),
            udpReachable = { false },
        )
        val list = strategy.buildCandidates()
        assertEquals(2, list.size) 
    }

    @Test
    fun pickBestRunsProbesInParallelNotSequentially() = runTest {
                        val slow1 = mockEngine(ProbeResult.Failure("slow1"), delayMs = 100L)
        val slow2 = mockEngine(ProbeResult.Failure("slow2"), delayMs = 100L)
        val slow3 = mockEngine(ProbeResult.Success(latencyMs = 1L), delayMs = 100L)

        val strategy = StrategyEngine(
            engines = mapOf(
                EngineId.XRAY to slow1,
                EngineId.AMNEZIA to slow2,
                EngineId.NAIVE to slow3,
            ),
            extraSources = listOf(
                CandidateSource {
                    listOf(
                        xrayCandidate(Candidate.PRIORITY_XRAY_VLESS_REALITY),
                        Candidate(EngineId.AMNEZIA, EngineConfig.Amnezia("{}", 10810), Candidate.PRIORITY_AMNEZIA),
                        Candidate(EngineId.NAIVE, EngineConfig.Naive("naive://x", 1080), 6),
                    )
                },
            ),
        )

        val tStart = testScheduler.currentTime
        val result = strategy.pickBest(strategy.buildCandidates())
        val elapsed = testScheduler.currentTime - tStart

        assertNotNull(result)
                assertTrue(elapsed < 250L, "ожидался parallel probe (~100мс), получено ${elapsed}мс")
    }
}
