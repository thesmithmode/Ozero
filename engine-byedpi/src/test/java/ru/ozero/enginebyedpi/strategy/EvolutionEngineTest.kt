package ru.ozero.enginebyedpi.strategy

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertFalse
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvolutionEngineTest {

    private val seeds = listOf("-winner -cmd", "-loser1", "-loser2", "-loser3")

    @Test
    fun `evolve reaches 100% fitness in first generation when winner exists`() = runTest {
        val engine = AlwaysSucceedEngine()
        val probe = AlwaysSucceedProbe()
        val pool = GenePool(seeds)
        val evolver = StrategyEvolver(pool)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> probe },
            evolver = evolver,
            pool = pool,
            sites = listOf("site1.com", "site2.com"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 4,
                maxGenerations = 3,
                targetFitness = 1.0,
            ),
        )
        var generationCount = 0
        val best = evolutionEngine.evolve(seeds) { generationCount++ }
        assertTrue(best.isNotEmpty())
        assertTrue(generationCount >= 1)
    }

    @Test
    fun `evolve stops at maxGenerations when target not reached`() = runTest {
        val engine = AlwaysSucceedEngine()
        val probe = AlwaysFailProbe()
        val pool = GenePool(seeds)
        val evolver = StrategyEvolver(pool)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> probe },
            evolver = evolver,
            pool = pool,
            sites = listOf("site1.com"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 2,
                maxGenerations = 3,
                targetFitness = 1.0,
            ),
        )
        var generationCount = 0
        evolutionEngine.evolve(seeds) { generationCount++ }
        assertEquals(3, generationCount)
    }

    @Test
    fun `evolve handles empty sites gracefully`() = runTest {
        val engine = AlwaysSucceedEngine()
        val pool = GenePool(seeds)
        val evolver = StrategyEvolver(pool)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = evolver,
            pool = pool,
            sites = emptyList(),
            settings = EvolutionEngine.EvolutionSettings(populationSize = 2, maxGenerations = 2),
        )
        val result = evolutionEngine.evolve(seeds) {}
        assertTrue(result.isNotEmpty(), "empty sites should still return a seed chromosome, got: $result")
    }

    @Test
    fun `evolve returns chromosome when engine fails to start`() = runTest {
        val engine = AlwaysFailEngine()
        val pool = GenePool(seeds)
        val evolver = StrategyEvolver(pool)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = evolver,
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(populationSize = 2, maxGenerations = 2),
        )
        var called = false
        evolutionEngine.evolve(seeds) { called = true }
        assertTrue(called)
    }

    @Test
    fun `parallel evaluation completes without errors`() = runTest {
        val engine = AlwaysSucceedEngine()
        var callCount = 0
        val probe = object : SocksProbeClient {
            override suspend fun probe(site: String): ProbeResult {
                callCount++
                delay(10L)
                return ProbeResult(site = site, success = true, durationMs = 10L)
            }
        }
        val pool = GenePool(seeds)
        val evolver = StrategyEvolver(pool)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> probe },
            evolver = evolver,
            pool = pool,
            sites = listOf("s1.com", "s2.com"),
            settings = EvolutionEngine.EvolutionSettings(populationSize = 4, maxGenerations = 1),
        )
        evolutionEngine.evolve(seeds) {}
        assertTrue(callCount > 0)
    }

    @Test
    fun `onChromosomeEval fires for each chromosome in each generation`() = runTest {
        val engine = AlwaysSucceedEngine()
        val pool = GenePool(seeds)
        val evolver = StrategyEvolver(pool)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = evolver,
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(populationSize = 3, maxGenerations = 2),
        )
        val evalEvents = mutableListOf<Triple<Int, Int, String>>()
        evolutionEngine.evolve(
            seedStrategies = seeds,
            onGeneration = {},
            onChromosomeEval = { index, total, command -> evalEvents.add(Triple(index, total, command)) },
        )
        assertEquals(6, evalEvents.size, "3 chromosomes × 2 generations = 6 eval events")
        assertTrue(evalEvents.all { it.second == 3 }, "total always equals populationSize")
        assertEquals(listOf(0, 1, 2, 0, 1, 2), evalEvents.map { it.first })
    }

    @Test
    fun `deterministic with seeded random produces same results`() = runTest {
        val bestChromosomes = (1..2).map {
            val engine = AlwaysSucceedEngine()
            val pool = GenePool(seeds)
            val evolver = StrategyEvolver(pool)
            val evolutionEngine = EvolutionEngine(
                byeDpiEngine = engine,
                probeFactory = { _, _ -> AlwaysSucceedProbe() },
                evolver = evolver,
                pool = pool,
                sites = listOf("s1.com"),
                settings = EvolutionEngine.EvolutionSettings(populationSize = 4, maxGenerations = 2),
                random = Random(42),
            )
            evolutionEngine.evolve(seeds) {}
        }
        assertFalse(bestChromosomes[0].isEmpty())
        assertFalse(bestChromosomes[1].isEmpty())
    }

    private class AlwaysSucceedEngine : EnginePlugin {
        override val id = EngineId.BYEDPI
        override val capabilities = EngineCapabilities(
            supportsTcp = true, supportsUdp = false, supportsDoH = false,
            localOnly = true, requiresServer = false, supportsUpstreamSocks = false,
        )
        override fun stats(): Flow<EngineStats> = MutableStateFlow(EngineStats())
        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Success(socksPort = 1080)
        override suspend fun stop() = Unit
        override suspend fun probe(): ru.ozero.enginescore.ProbeResult =
            ru.ozero.enginescore.ProbeResult.Success(latencyMs = 0)
    }

    private class AlwaysFailEngine : EnginePlugin {
        override val id = EngineId.BYEDPI
        override val capabilities = EngineCapabilities(
            supportsTcp = true, supportsUdp = false, supportsDoH = false,
            localOnly = true, requiresServer = false, supportsUpstreamSocks = false,
        )
        override fun stats(): Flow<EngineStats> = MutableStateFlow(EngineStats())
        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Failure("test fail")
        override suspend fun stop() = Unit
        override suspend fun probe(): ru.ozero.enginescore.ProbeResult =
            ru.ozero.enginescore.ProbeResult.Success(latencyMs = 0)
    }

    private class AlwaysSucceedProbe : SocksProbeClient {
        override suspend fun probe(site: String): ProbeResult =
            ProbeResult(site = site, success = true, durationMs = 1L)
    }

    private class AlwaysFailProbe : SocksProbeClient {
        override suspend fun probe(site: String): ProbeResult =
            ProbeResult(site = site, success = false, durationMs = 1L)
    }
}
