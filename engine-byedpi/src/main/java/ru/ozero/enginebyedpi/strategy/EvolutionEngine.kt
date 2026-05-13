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
        val stagnationCount: Int = 0,
    )

    suspend fun evolve(
        seedStrategies: List<String>,
        onGeneration: (GenerationResult) -> Unit,
        onChromosomeEval: (index: Int, total: Int, command: String) -> Unit = { _, _, _ -> },
    ): Chromosome {
        var population = buildInitialPopulation(seedStrategies)
        var best: Chromosome = population.firstOrNull() ?: return emptyList()
        var bestFitness = 0.0
        var stagnationCount = 0

        for (generation in 1..settings.maxGenerations) {
            if (!currentCoroutineContext().isActive) break

            val effectiveMutationRate = if (stagnationCount >= 2) {
                (settings.mutationRate * (1f + stagnationCount * 0.5f)).coerceAtMost(0.9f)
            } else {
                settings.mutationRate
            }

            val scored = evaluatePopulation(population, onChromosomeEval)
            val genBest = scored.maxByOrNull { it.second }
            if (genBest != null && genBest.second > bestFitness) {
                bestFitness = genBest.second
                best = genBest.first
                stagnationCount = 0
            } else {
                stagnationCount++
            }
            onGeneration(
                GenerationResult(
                    generation = generation,
                    best = best,
                    bestFitness = bestFitness,
                    population = scored,
                    stagnationCount = stagnationCount,
                ),
            )
            memory?.save()
            if (bestFitness >= settings.targetFitness) break
            if (stagnationCount >= settings.maxGenerations / 2) break

            val survivors = evolver.select(scored, settings.eliteCount)
            population = if (stagnationCount >= 2) {
                buildDiverseGeneration(survivors, effectiveMutationRate)
            } else {
                buildNextGeneration(survivors, effectiveMutationRate)
            }
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

    private fun buildNextGeneration(
        survivors: List<Chromosome>,
        mutationRate: Float = settings.mutationRate,
    ): List<Chromosome> {
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
            offspring.add(evolver.mutate(evolver.crossover(p1, p2), mutationRate, memory = memory))
        }
        return offspring
    }

    private fun buildDiverseGeneration(
        elites: List<Chromosome>,
        mutationRate: Float,
    ): List<Chromosome> {
        val offspring = mutableListOf<Chromosome>()
        offspring.addAll(elites)
        val randomCount = (settings.populationSize - elites.size) / 2
        val mutatedCount = settings.populationSize - elites.size - randomCount
        repeat(randomCount) {
            offspring.add(
                if (memory != null && memory.hasData()) {
                    pool.weightedRandomChromosome(memory, random = random)
                } else {
                    pool.randomChromosome(random = random)
                },
            )
        }
        repeat(mutatedCount) {
            val parent = elites[random.nextInt(elites.size)]
            offspring.add(evolver.mutate(parent, mutationRate, random = random, memory = memory))
        }
        return offspring
    }

    private suspend fun evaluatePopulation(
        population: List<Chromosome>,
        onChromosomeEval: (index: Int, total: Int, command: String) -> Unit = { _, _, _ -> },
    ): List<Pair<Chromosome, Double>> {
        val results = population.mapIndexed { index, chromosome ->
            if (!currentCoroutineContext().isActive) return@mapIndexed chromosome to 0.0
            onChromosomeEval(index, population.size, chromosome.toCommand())
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
