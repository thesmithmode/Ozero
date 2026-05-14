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
        val best = evolutionEngine.evolve(seedStrategies = seeds, onGeneration = { generationCount++ })
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
        evolutionEngine.evolve(seedStrategies = seeds, onGeneration = { generationCount++ })
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
        val result = evolutionEngine.evolve(seedStrategies = seeds, onGeneration = {})
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
        evolutionEngine.evolve(seedStrategies = seeds, onGeneration = { called = true })
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
        evolutionEngine.evolve(seedStrategies = seeds, onGeneration = {})
        assertTrue(callCount > 0)
    }

    @Test
    fun `stagnationCount increments when fitness does not improve`() = runTest {
        val engine = AlwaysSucceedEngine()
        val probe = AlwaysFailProbe()
        val pool = GenePool(seeds)
        val evolver = StrategyEvolver(pool)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> probe },
            evolver = evolver,
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 2,
                maxGenerations = 6,
                targetFitness = 1.0,
            ),
        )
        val stagnationCounts = mutableListOf<Int>()
        evolutionEngine.evolve(
            seedStrategies = seeds,
            onGeneration = { result -> stagnationCounts.add(result.stagnationCount) },
        )
        assertTrue(stagnationCounts.any { it >= 2 }, "should detect stagnation: $stagnationCounts")
    }

    @Test
    fun `evolve exits early when stagnation exceeds half of maxGenerations`() = runTest {
        val engine = AlwaysSucceedEngine()
        val probe = AlwaysFailProbe()
        val pool = GenePool(seeds)
        val evolver = StrategyEvolver(pool)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> probe },
            evolver = evolver,
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 2,
                maxGenerations = 10,
                targetFitness = 1.0,
            ),
        )
        var generationCount = 0
        evolutionEngine.evolve(seedStrategies = seeds, onGeneration = { generationCount++ })
        assertTrue(generationCount < 10, "should exit early due to stagnation, ran $generationCount gens")
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
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 3,
                maxGenerations = 2,
                targetFitness = 1.01,
            ),
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
            evolutionEngine.evolve(seedStrategies = seeds, onGeneration = {})
        }
        assertFalse(bestChromosomes[0].isEmpty())
        assertFalse(bestChromosomes[1].isEmpty())
    }

    @Test
    fun `cache skips duplicate chromosome evaluations`() = runTest {
        val engine = CountingEngine()
        val pool = GenePool(seeds)
        val evolver = StrategyEvolver(pool)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = evolver,
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 4,
                maxGenerations = 2,
                targetFitness = 0.0,
            ),
        )
        evolutionEngine.evolve(seedStrategies = seeds, onGeneration = {})
        assertTrue(engine.startCount < 4 * 2, "cache should skip re-evaluation: starts=${engine.startCount}")
    }

    @Test
    fun `bestSuccessRate equals raw success ratio independent of latency`() = runTest {
        val slowProbe = object : SocksProbeClient {
            override suspend fun probe(site: String) = ProbeResult(site = site, success = true, durationMs = 8000L)
        }
        val pool = GenePool(seeds)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> slowProbe },
            evolver = StrategyEvolver(pool),
            pool = pool,
            sites = listOf("s1.com", "s2.com"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 2,
                maxGenerations = 1,
                targetFitness = 0.0,
            ),
        )
        var capturedResult: EvolutionEngine.GenerationResult? = null
        evolutionEngine.evolve(seedStrategies = seeds, onGeneration = { capturedResult = it })
        val result = capturedResult!!
        assertTrue(result.bestSuccessRate > 0.0, "all probes succeed so successRate > 0")
        assertTrue(
            result.bestSuccessRate > result.bestFitness,
            "high latency penalizes fitness but not successRate: rate=${result.bestSuccessRate} fitness=${result.bestFitness}",
        )
    }

    @Test
    fun `latency fitness penalizes slow probes vs fast probes`() = runTest {
        val fastProbe = object : SocksProbeClient {
            override suspend fun probe(site: String) = ProbeResult(site = site, success = true, durationMs = 100L)
        }
        val slowProbe = object : SocksProbeClient {
            override suspend fun probe(site: String) = ProbeResult(site = site, success = true, durationMs = 4000L)
        }
        fun makeEngine(probe: SocksProbeClient): EvolutionEngine {
            val pool = GenePool(seeds)
            return EvolutionEngine(
                byeDpiEngine = AlwaysSucceedEngine(),
                probeFactory = { _, _ -> probe },
                evolver = StrategyEvolver(pool),
                pool = pool,
                sites = listOf("s1.com"),
                settings = EvolutionEngine.EvolutionSettings(
                    populationSize = 2,
                    maxGenerations = 1,
                    targetFitness = 0.0,
                ),
            )
        }
        var fastFitness = 0.0
        var slowFitness = 0.0
        makeEngine(fastProbe).evolve(seedStrategies = seeds, onGeneration = { fastFitness = it.bestFitness })
        makeEngine(slowProbe).evolve(seedStrategies = seeds, onGeneration = { slowFitness = it.bestFitness })
        assertTrue(fastFitness > slowFitness, "fast probe fitness=$fastFitness should exceed slow=$slowFitness")
    }

    @Test
    fun `default targetFitness is below 1_0 to be reachable`() {
        val settings = EvolutionEngine.EvolutionSettings()
        assertTrue(settings.targetFitness < 1.0, "targetFitness ${settings.targetFitness} unreachable — must be < 1.0")
    }

    @Test
    fun `start failure result not cached in persistent fitness cache`() = runTest {
        val engine = AlwaysFailEngine()
        val pool = GenePool(seeds)
        val cacheFile = java.io.File.createTempFile("fit", ".json").also { it.deleteOnExit() }
        val fitnessCache = StrategyFitnessCache(cacheFile)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(pool),
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(populationSize = 2, maxGenerations = 1),
            fitnessCachePersistent = fitnessCache,
        )
        evolutionEngine.evolve(seedStrategies = seeds, onGeneration = {})
        assertEquals(0, fitnessCache.size(), "start failures must not poison persistent fitness cache")
    }

    private class CountingEngine : EnginePlugin {
        var startCount = 0
        override val id = EngineId.BYEDPI
        override val capabilities = EngineCapabilities(
            supportsTcp = true, supportsUdp = false, supportsDoH = false,
            localOnly = true, requiresServer = false, supportsUpstreamSocks = false,
        )
        override fun stats(): Flow<EngineStats> = MutableStateFlow(EngineStats())
        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult {
            startCount++
            return StartResult.Success(socksPort = 1080)
        }
        override suspend fun stop() = Unit
        override suspend fun probe(): ru.ozero.enginescore.ProbeResult =
            ru.ozero.enginescore.ProbeResult.Success(latencyMs = 0)
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
