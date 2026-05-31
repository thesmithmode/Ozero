package ru.ozero.enginebyedpi.strategy

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class EvolutionEngineEvaluationLifecycleTest {

    @Test
    fun `onCommandEvaluated reports only commands that reached engine start`() = runTest {
        val engine = CountingEngine()
        val validSeeds = listOf("-K -An -s2 -d1 -a1")
        val invalidSeeds = listOf("google.com -Qr -a1")
        val pool = GenePool(validSeeds + invalidSeeds)
        val evaluated = mutableListOf<String>()
        val evolutionEngine = EvolutionEngine(
            byeDpiEngine = engine,
            probeFactory = { _, _ -> AlwaysSucceedProbe() },
            evolver = StrategyEvolver(pool),
            pool = pool,
            sites = listOf("s1.com"),
            settings = EvolutionEngine.EvolutionSettings(
                populationSize = 2,
                maxGenerations = 1,
                targetFitness = 1.01,
                initialSeedRatio = 1.0,
                initialMemoryRatio = 0.0,
            ),
        )
        evolutionEngine.evolve(
            seedStrategies = validSeeds + invalidSeeds,
            onGeneration = {},
            onCommandEvaluated = { evaluated += it },
        )
        assertTrue(validSeeds.first() in evaluated)
        assertTrue(invalidSeeds.first() !in evaluated)
    }
}
