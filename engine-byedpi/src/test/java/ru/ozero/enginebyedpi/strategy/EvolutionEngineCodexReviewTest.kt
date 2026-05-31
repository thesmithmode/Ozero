package ru.ozero.enginebyedpi.strategy

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvolutionEngineCodexReviewTest {

    @Test
    fun `valid detached short option seed reaches engine start`() = runTest {
        val engine = CountingEngine()
        val validSeeds = listOf("-s 25+s -t5 -a1")
        val pool = GenePool(validSeeds)
        val evaluated = mutableListOf<String>()
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(pool),
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 1,
                maxGenerations = 1,
                targetFitness = 1.0,
            ),
        )
        evolutionEngine.evolve(
            seedStrategies = validSeeds,
            onGeneration = {},
            onCommandEvaluated = { evaluated += it },
        )
        assertEquals(1, engine.startCount, "detached short option must be evaluated, not rejected before start")
        assertTrue(validSeeds.first() in evaluated)
    }

    @Test
    fun `default evaluation timeout scales with serial probe batches`() = runTest {
        val engine = CountingEngine()
        var probeCount = 0
        val slowProbe = object : SocksProbeClient {
            override suspend fun probe(site: String): ProbeResult {
                probeCount++
                delay(PROBE_DELAY_MS)
                return ProbeResult(site = site, success = true, durationMs = PROBE_DELAY_MS)
            }
        }
        val validSeeds = listOf("-K -An -s2 -d1 -a1")
        val pool = GenePool(validSeeds)
        val sites = (1..SITE_COUNT).map { "site$it.com" }
        val evaluated = mutableListOf<String>()
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> slowProbe },
            evolver = StrategyEvolver(pool),
            pool = pool,
            sites = sites,
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 1,
                maxGenerations = 1,
                concurrentProbes = 1,
                timeoutMs = PROBE_DELAY_MS,
                targetFitness = 1.0,
            ),
        )
        evolutionEngine.evolve(
            seedStrategies = validSeeds,
            onGeneration = {},
            onCommandEvaluated = { evaluated += it },
        )
        assertEquals(sites.size, probeCount)
        assertEquals(listOf(validSeeds.first()), evaluated)
    }

    private companion object {
        const val PROBE_DELAY_MS = 1_000L
        const val SITE_COUNT = 10
    }
}
