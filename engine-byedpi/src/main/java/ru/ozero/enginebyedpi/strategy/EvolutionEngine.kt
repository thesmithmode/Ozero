package ru.ozero.enginebyedpi.strategy

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import kotlin.random.Random

class EvolutionEngine(
    private val byeDpiEngine: EnginePlugin,
    private val probeFactory: (socksPort: Int, timeoutMs: Long) -> SocksProbeClient,
    private val evolver: StrategyEvolver,
    private val pool: GenePool,
    private val sites: List<String>,
    private val settings: EvolutionSettings = EvolutionSettings(),
    private val socksPort: Int = 1080,
    private val memory: GeneMemory? = null,
    private val random: Random = Random.Default,
) {

    data class EvolutionSettings(
        val populationSize: Int = 20,
        val maxGenerations: Int = 10,
        val mutationRate: Float = 0.2f,
        val eliteCount: Int = 5,
        val targetFitness: Double = 1.0,
        val concurrentProbes: Int = 10,
        val timeoutMs: Long = 5_000L,
    )

    data class GenerationResult(
        val generation: Int,
        val best: Chromosome,
        val bestFitness: Double,
        val population: List<Pair<Chromosome, Double>>,
    )

    suspend fun evolve(
        seedStrategies: List<String>,
        onGeneration: (GenerationResult) -> Unit,
    ): Chromosome {
        var population = buildInitialPopulation(seedStrategies)
        var best: Chromosome = population.firstOrNull() ?: return emptyList()
        var bestFitness = 0.0

        for (generation in 1..settings.maxGenerations) {
            if (!currentCoroutineContext().isActive) break

            val scored = evaluatePopulation(population)
            val genBest = scored.maxByOrNull { it.second }
            if (genBest != null && genBest.second > bestFitness) {
                bestFitness = genBest.second
                best = genBest.first
            }
            onGeneration(
                GenerationResult(
                    generation = generation,
                    best = best,
                    bestFitness = bestFitness,
                    population = scored,
                ),
            )
            memory?.save()
            if (bestFitness >= settings.targetFitness) break

            val survivors = evolver.select(scored, settings.eliteCount)
            population = buildNextGeneration(survivors)
        }
        return best
    }

    private fun buildInitialPopulation(seedStrategies: List<String>): List<Chromosome> {
        val fromSeeds = seedStrategies.take(settings.populationSize).map(::parseChromosome)
        val fillCount = (settings.populationSize - fromSeeds.size).coerceAtLeast(0)
        val fill = if (memory != null && memory.hasData()) {
            List(fillCount) { pool.weightedRandomChromosome(memory) }
        } else {
            List(fillCount) { pool.randomChromosome() }
        }
        return fromSeeds + fill
    }

    private fun buildNextGeneration(survivors: List<Chromosome>): List<Chromosome> {
        if (survivors.isEmpty()) {
            return if (memory != null && memory.hasData()) {
                List(settings.populationSize) { pool.weightedRandomChromosome(memory) }
            } else {
                List(settings.populationSize) { pool.randomChromosome() }
            }
        }
        val offspring = mutableListOf<Chromosome>()
        offspring.addAll(survivors)
        while (offspring.size < settings.populationSize) {
            val p1 = survivors[random.nextInt(survivors.size)]
            val p2 = survivors[random.nextInt(survivors.size)]
            offspring.add(evolver.mutate(evolver.crossover(p1, p2), settings.mutationRate, memory = memory))
        }
        return offspring
    }

    private suspend fun evaluatePopulation(
        population: List<Chromosome>,
    ): List<Pair<Chromosome, Double>> {
        val results = population.map { chromosome ->
            if (!currentCoroutineContext().isActive) return@map chromosome to 0.0
            chromosome to evaluate(chromosome)
        }
        results.forEach { (chromosome, fitness) ->
            memory?.record(chromosome.map { it.token }, fitness)
        }
        return results
    }

    private suspend fun evaluate(chromosome: Chromosome): Double {
        if (sites.isEmpty() || chromosome.isEmpty()) return 0.0
        val command = chromosome.toCommand()
        val started = byeDpiEngine.start(
            config = EngineConfig.ByeDpi(args = command, socksPort = socksPort),
            upstream = Upstream.None,
        )
        if (started !is StartResult.Success) {
            runCatching { byeDpiEngine.stop() }
            return 0.0
        }
        return try {
            val probe = probeFactory(socksPort, settings.timeoutMs)
            val semaphore = Semaphore(settings.concurrentProbes.coerceAtLeast(1))
            coroutineScope {
                sites.map { site ->
                    async {
                        semaphore.withPermit {
                            runCatching { probe.probe(site) }.getOrNull()?.success == true
                        }
                    }
                }.map { it.await() }
            }.count { it }.toDouble() / sites.size
        } finally {
            runCatching { byeDpiEngine.stop() }
        }
    }
}
