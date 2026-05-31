package ru.ozero.enginebyedpi.strategy

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvolutionEngineSentinelTest {

    private val seeds = listOf(
        "-K -An -s2 -d1",
        "-Ku -a1 -An",
        "-K -r1+s -An",
        "-K -s1 -q1",
    )

    @Test
    fun `no mutation rate boost on stagnation — constant mutationRate`() {
        val source = File(System.getProperty("user.dir") ?: ".")
            .resolve("src/main/java/ru/ozero/enginebyedpi/strategy/EvolutionEngine.kt").readText()
        assertTrue(
            !source.contains("stagnationCount * 0.5f") && !source.contains("stagnationCount * 0.5"),
            "Mutation rate boost on stagnation is a костыль removed in favour of granular fitness. " +
                "Do not re-introduce it.",
        )
    }

    @Test
    fun `default targetFitness is below 1_0 to be reachable`() {
        val settings = EvolutionEngine.EvolutionSettings()
        assertTrue(settings.targetFitness < 1.0, "targetFitness ${settings.targetFitness} unreachable — must be < 1.0")
    }

    @Test
    fun `default settings reflect v2 parameters`() {
        val s = EvolutionEngine.EvolutionSettings()
        assertEquals(30, s.populationSize, "v2 default populationSize must be 30")
        assertEquals(20, s.maxGenerations, "v2 default maxGenerations must be 20")
        assertEquals(3, s.eliteCount, "v2 default eliteCount must be 3")
        assertEquals(0.85, s.targetFitness, "v2 default targetFitness must be 0.85")
        assertEquals(0.4, s.initialSeedRatio, "v2 seed quota = 40%")
        assertEquals(0.3, s.initialMemoryRatio, "v2 memory quota = 30%")
        assertEquals(3_000.0, s.latencyClampMs, "v2 latency clamp = 3000ms")
        assertEquals(1.5, s.successRateExponent, "v2 successRate exponent = 1.5")
    }

    @Test
    fun `survivor count scales with populationSize as diversity floor`() {
        val source = File(System.getProperty("user.dir") ?: ".")
            .resolve("src/main/java/ru/ozero/enginebyedpi/strategy/EvolutionEngine.kt").readText()
        assertTrue(
            source.contains("populationSize / 4"),
            "Tournament survivors must scale with population: (populationSize/4).coerceAtLeast(eliteCount). " +
                "Fixed eliteCount=3 collapses diversity by gen 2.",
        )
    }

    @Test
    fun `EvolutionEngine uses adaptive memory ratio for rich memory`() {
        val source = File(System.getProperty("user.dir") ?: ".")
            .resolve("src/main/java/ru/ozero/enginebyedpi/strategy/EvolutionEngine.kt").readText()
        assertTrue(
            source.contains("ADAPTIVE_SEED_RATIO") && source.contains("ADAPTIVE_MEMORY_RATIO"),
            "EvolutionEngine must define adaptive ratios for rich memory",
        )
        assertTrue(
            source.contains("memory.isRich()") || source.contains("memoryRich"),
            "EvolutionEngine must switch ratios based on memory.isRich()",
        )
    }

    @Test
    fun `evaluate is timeout bounded and cleanup is non cancellable`() {
        val source = File(System.getProperty("user.dir") ?: ".")
            .resolve("src/main/java/ru/ozero/enginebyedpi/strategy/EvolutionEngine.kt").readText()
        assertTrue(source.contains("withTimeout(settings.evaluationTimeoutMs"))
        assertTrue(source.contains("NonCancellable"))
        assertTrue(source.contains("withTimeoutOrNull(settings.stopTimeoutMs"))
    }

    @Test
    fun `reduceChromosome runs only after best improves`() {
        val source = File(System.getProperty("user.dir") ?: ".")
            .resolve("src/main/java/ru/ozero/enginebyedpi/strategy/EvolutionEngine.kt").readText()
        assertTrue(source.contains("if (bestImproved)"))
        assertTrue(source.contains("maxReductionEvaluations"))
    }

    @Test
    fun `start failure result not cached in persistent fitness cache`() = runTest {
        val engine = AlwaysFailEngine()
        val pool = GenePool(seeds)
        val cacheFile = File.createTempFile("fit", ".json").also { it.deleteOnExit() }
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

    @Test
    fun `persistent fitness cache write-only during evolve - stale fitness not read`() = runTest {
        val countingEngine = CountingEngine()
        val pool = GenePool(seeds)
        val cacheFile = File.createTempFile("fit", ".json").also { it.deleteOnExit() }
        val fitnessCache = StrategyFitnessCache(cacheFile)
        seeds.forEach { seed -> fitnessCache.put(seed, 0.99) }
        assertEquals(seeds.size, fitnessCache.size(), "precondition: all seeds in cache")
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = countingEngine,
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(pool),
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(populationSize = 4, maxGenerations = 1),
            fitnessCachePersistent = fitnessCache,
        )
        evolutionEngine.evolve(seedStrategies = seeds, onGeneration = {})
        assertTrue(
            countingEngine.startCount > 0,
            "persistent cache must not be read during evolve — stale fitness bypassed, " +
                "engine.startCount=${countingEngine.startCount}",
        )
    }

    @Test
    fun `adaptive memory ratio reduces seed proportion when memory is rich`() = runTest {
        val manySeeds = (1..15).map { "-S$it" }

        val poorFile = File.createTempFile("poor", ".json").also { it.deleteOnExit() }
        val poorMem = GeneMemory(poorFile)

        val richFile = File.createTempFile("rich", ".json").also { it.deleteOnExit() }
        val richMem = GeneMemory(richFile)
        repeat(RICH_RECORDS) { richMem.record(listOf("-mA", "-mB"), fitness = 0.8) }
        assertTrue(richMem.isRich(), "precondition: 60 records must trigger isRich")

        val poorCmds = collectGenerationOneCommands(poorMem, manySeeds)
        val richCmds = collectGenerationOneCommands(richMem, manySeeds)

        val seedSet = manySeeds.toSet()
        val poorSeedCount = poorCmds.count { it in seedSet }
        val richSeedCount = richCmds.count { it in seedSet }

        assertTrue(
            richSeedCount < poorSeedCount,
            "rich memory must reduce seed-derived chromosomes in initial population: " +
                "rich=$richSeedCount poor=$poorSeedCount",
        )
    }

    private suspend fun collectGenerationOneCommands(memory: GeneMemory, seedList: List<String>): List<String> {
        val pool = GenePool(seedList)
        val captured = mutableListOf<String>()
        EvolutionEngine(
            byeDpiEngine = AlwaysSucceedEngine(),
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(pool),
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = POP_SIZE_GEN1,
                maxGenerations = 1,
                targetFitness = 0.0,
            ),
            memory = memory,
            random = Random(SEED_DEFAULT),
        ).evolve(
            seedStrategies = seedList,
            onGeneration = {},
            onChromosomeEval = { _, _, cmd -> captured.add(cmd) },
        )
        return captured.take(POP_SIZE_GEN1)
    }

    private companion object {
        const val RICH_RECORDS = 60
        const val POP_SIZE_GEN1 = 30
        const val SEED_DEFAULT = 42L
    }
}
