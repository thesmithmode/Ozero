package ru.ozero.enginebyedpi.strategy

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvolutionEngineTest {

    private val seeds = listOf(
        "-K -An -s2 -d1",
        "-Ku -a1 -An",
        "-K -r1+s -An",
        "-K -s1 -q1",
    )

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
            sites = listOf("s1.com"),
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
    fun `diverse generation handles empty elites`() {
        val pool = GenePool(seeds)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(pool),
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 4,
                eliteCount = 0,
            ),
            random = Random(7),
        )
        val method = EvolutionEngine::class.java.getDeclaredMethod(
            "buildDiverseGeneration",
            List::class.java,
            Float::class.javaPrimitiveType,
        )
        method.isAccessible = true
        val generation = method.invoke(evolutionEngine, emptyList<Chromosome>(), 0.2f) as List<*>
        assertEquals(4, generation.size)
        assertTrue(generation.all { it is Chromosome })
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
    fun `invalid chromosomes are skipped before native start`() = runTest {
        val engine = CountingEngine()
        val invalidSeeds = listOf("google.com -Qr -a1")
        val pool = GenePool(invalidSeeds)
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
        evolutionEngine.evolve(seedStrategies = invalidSeeds, onGeneration = {})
        assertEquals(0, engine.startCount, "invalid argv must not reach native ByeDPI start")
        assertEquals(0, engine.stopCount, "skip-before-start must not call stop when no start attempt happened")
    }

    @Test
    fun `start timeout still stops engine after start attempt`() = runTest {
        val engine = HangingStartEngine()
        val validSeeds = listOf("-K -An -s2 -d1 -a1")
        val pool = GenePool(validSeeds)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(pool),
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 1,
                maxGenerations = 1,
                evaluationTimeoutMs = 50L,
                stopTimeoutMs = 50L,
            ),
        )
        evolutionEngine.evolve(seedStrategies = validSeeds, onGeneration = {})
        assertEquals(1, engine.startCount)
        assertEquals(1, engine.stopCount, "stop must run in NonCancellable cleanup after a timed-out start")
    }

    @Test
    fun `reduceChromosome respects evaluation budget`() = runTest {
        val engine = CountingEngine()
        val validSeeds = listOf("-K -An -s2 -d1 -a1")
        val pool = GenePool(validSeeds)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(pool),
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 1,
                maxGenerations = 1,
                targetFitness = 1.01,
                maxReductionEvaluations = 1,
            ),
        )
        evolutionEngine.evolve(seedStrategies = validSeeds, onGeneration = {})
        assertTrue(engine.startCount <= 2, "population eval plus one reduction eval expected, got ${engine.startCount}")
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
            "high latency penalizes fitness but not successRate: " +
                "rate=${result.bestSuccessRate} fitness=${result.bestFitness}",
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
    fun `computeProbeScore returns 1_0 for full success`() {
        val engine = EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(GenePool(seeds)),
            pool = GenePool(seeds),
            sites = listOf("s1"),
        )
        assertEquals(1.0, engine.computeProbeScore(ProbeResult(site = "s", success = true, durationMs = 1L)))
    }

    @Test
    fun `computeProbeScore returns 0_0 for null`() {
        val engine = EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(GenePool(seeds)),
            pool = GenePool(seeds),
            sites = listOf("s1"),
        )
        assertEquals(0.0, engine.computeProbeScore(null))
    }

    @Test
    fun `computeProbeScore returns partial score for HTTP response without full content`() {
        val engine = EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(GenePool(seeds)),
            pool = GenePool(seeds),
            sites = listOf("s1"),
        )
        val headersOnly = engine.computeProbeScore(
            ProbeResult(site = "s", success = false, durationMs = 1L, responseCode = 200, actualLength = 0L),
        )
        val withContent = engine.computeProbeScore(
            ProbeResult(site = "s", success = false, durationMs = 1L, responseCode = 200, actualLength = 512L),
        )
        val noResponse = engine.computeProbeScore(
            ProbeResult(site = "s", success = false, durationMs = 1L),
        )
        assertTrue(headersOnly > 0.0, "HTTP headers bypassed DPI — partial credit")
        assertTrue(withContent > headersOnly, "partial content scores higher than headers only")
        assertEquals(0.0, noResponse, "no HTTP response = fully blocked")
    }

    @Test
    fun `computeProbeScore treats all 1xx-5xx response codes as DPI bypass`() {
        val engine = EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(GenePool(seeds)),
            pool = GenePool(seeds),
            sites = listOf("s1"),
        )
        for (code in listOf(100, 200, 301, 403, 500, 599)) {
            val score = engine.computeProbeScore(
                ProbeResult(site = "s", success = false, durationMs = 1L, responseCode = code),
            )
            assertTrue(score > 0.0, "responseCode=$code should be > 0 (DPI bypassed)")
        }
        assertEquals(
            0.0,
            engine.computeProbeScore(ProbeResult(site = "s", success = false, durationMs = 1L, responseCode = -1)),
            "responseCode=-1 means no HTTP response = DPI blocked",
        )
    }

    @Test
    fun `granular fitness produces non-zero score for HTTP-only probes`() = runTest {
        val httpOnlyProbe = object : SocksProbeClient {
            override suspend fun probe(site: String) = ProbeResult(
                site = site,
                success = false,
                durationMs = 100L,
                responseCode = 200,
                actualLength = 512L,
            )
        }
        val pool = GenePool(seeds)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> httpOnlyProbe },
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
        assertTrue(result.bestFitness > 0.0, "HTTP-only probes must produce non-zero fitness (granular scoring)")
        assertEquals(0.0, result.bestSuccessRate, "successRate = 0 since no probe was fully successful")
    }

    @Test
    fun `computeFitness clamps latency above ceiling to zero score`() {
        val engine = EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(GenePool(seeds)),
            pool = GenePool(seeds),
            sites = listOf("s1"),
        )
        val above = engine.computeFitness(successRate = 1.0, avgLatencyMs = 5_000.0)
        val atCeiling = engine.computeFitness(successRate = 1.0, avgLatencyMs = 3_000.0)
        val below = engine.computeFitness(successRate = 1.0, avgLatencyMs = 100.0)
        assertEquals(0.0, above, "5000ms must clamp to ceiling → fitness 0")
        assertEquals(0.0, atCeiling, "3000ms (ceiling) → fitness 0")
        assertTrue(below > 0.9, "100ms latency → near-1 fitness, got $below")
    }

    @Test
    fun `computeFitness exponentially penalises partial success rate`() {
        val engine = EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(GenePool(seeds)),
            pool = GenePool(seeds),
            sites = listOf("s1"),
        )
        val full = engine.computeFitness(successRate = 1.0, avgLatencyMs = 100.0)
        val half = engine.computeFitness(successRate = 0.5, avgLatencyMs = 100.0)
        assertTrue(full > 0.0 && half > 0.0)
        assertTrue(half / full < 0.5, "exponent 1.5 must penalise 50% rate harder than linear: ratio=${half / full}")
    }

    @Test
    fun `computeFitness returns zero for zero success regardless of latency`() {
        val engine = EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(GenePool(seeds)),
            pool = GenePool(seeds),
            sites = listOf("s1"),
        )
        assertEquals(0.0, engine.computeFitness(0.0, 50.0))
        assertEquals(0.0, engine.computeFitness(0.0, 0.0))
    }

    @Test
    fun `initial population mixes seed memory random per v2 ratios`() = runTest {
        val abundantSeeds = List(100) { "-seed$it" }
        val engine = EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(GenePool(abundantSeeds)),
            pool = GenePool(abundantSeeds),
            sites = listOf("s1"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 10,
                maxGenerations = 1,
                targetFitness = 0.0,
            ),
            random = Random(0),
        )
        val commands = mutableSetOf<String>()
        engine.evolve(
            seedStrategies = abundantSeeds,
            onGeneration = {},
            onChromosomeEval = { _, _, cmd -> commands.add(cmd) },
        )
        assertTrue(commands.size >= 5, "v2 mix must produce diverse population, distinct=${commands.size}")
    }

    @Test
    fun `evaluate использует уникальный rotated port каждый старт — байпасс TIME_WAIT`() = runTest {
        val engine = PortRecordingEngine()
        val pool = GenePool(seeds)
        val evolver = StrategyEvolver(pool)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> AlwaysFailProbe() },
            evolver = evolver,
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 30,
                maxGenerations = 1,
                portRotationBase = 49_152,
                portRotationRange = 256,
            ),
        )
        evolutionEngine.evolve(seedStrategies = seeds, onGeneration = {})
        assertTrue(engine.ports.isNotEmpty(), "ожидалось ≥1 evaluate.start")
        engine.ports.forEach { p ->
            assertTrue(p in 49_152..49_407, "port $p out of rotation range")
        }
        val unique = engine.ports.toSet()
        assertEquals(
            engine.ports.size,
            unique.size,
            "каждый evaluate должен получить уникальный port — TIME_WAIT байпасс. " +
                "Дубль port → bind на тот же port что в TIME_WAIT → main() byedpi возвращает -1 серией",
        )
    }

    @Test
    fun `port rotation возвращается к base после исчерпания range`() = runTest {
        val engine = PortRecordingEngine()
        val pool = GenePool(seeds)
        val evolver = StrategyEvolver(pool)
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> AlwaysFailProbe() },
            evolver = evolver,
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 5,
                maxGenerations = 1,
                portRotationBase = 50_000,
                portRotationRange = 3,
            ),
        )
        evolutionEngine.evolve(seedStrategies = seeds, onGeneration = {})
        val expectedSet = setOf(50_000, 50_001, 50_002)
        assertTrue(
            engine.ports.toSet().all { it in expectedSet },
            "ports должны быть в range 50000..50002, got=${engine.ports}",
        )
    }

    @Test
    fun `selectSurvivors returns empty for empty fitness pairs`() {
        val pool = GenePool(seeds)
        val engine = EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(pool),
            pool = pool,
            sites = listOf("s1"),
            random = Random(0),
        )
        val result = engine.selectSurvivors(fitnessPairs = emptyList(), survivorCount = 5)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `selectSurvivors returns only elites when survivorExplorationRate is zero`() {
        val pool = GenePool(seeds)
        val engine = EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(pool),
            pool = pool,
            sites = listOf("s1"),
            settings = EvolutionEngine.EvolutionSettings(survivorExplorationRate = 0.0),
            random = Random(0),
        )
        val pairs = listOf<Pair<Chromosome, Double>>(
            listOf(StrategyGene("-a")) to 0.9,
            listOf(StrategyGene("-b")) to 0.1,
            listOf(StrategyGene("-c")) to 0.5,
        )
        val survivors = engine.selectSurvivors(pairs, survivorCount = 2)
        assertEquals(2, survivors.size)
        assertTrue(survivors.all { ch -> pairs.any { it.first == ch } })
    }

    @Test
    fun `selectSurvivors mixes elites and tail-window explorers when exploration enabled`() {
        val pool = GenePool(seeds)
        val engine = EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(pool),
            pool = pool,
            sites = listOf("s1"),
            settings = EvolutionEngine.EvolutionSettings(survivorExplorationRate = 0.5),
            random = Random(0),
        )
        val pairs = (0..9).map { listOf(StrategyGene("-g$it")) to it.toDouble() / 10.0 }
        val survivors = engine.selectSurvivors(pairs, survivorCount = 4)
        assertTrue(survivors.size <= 4)
        assertTrue(survivors.isNotEmpty())
        assertTrue(survivors.all { ch -> pairs.any { it.first == ch } })
    }
}
