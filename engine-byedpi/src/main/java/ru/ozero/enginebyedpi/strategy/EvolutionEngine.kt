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
    private val fitnessCachePersistent: StrategyFitnessCache? = null,
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
        val bestSuccessRate: Double,
        val population: List<Pair<Chromosome, Double>>,
        val stagnationCount: Int = 0,
    )

    private data class EvalResult(val fitness: Double, val successRate: Double)

    suspend fun evolve(
        seedStrategies: List<String>,
        onGeneration: (GenerationResult) -> Unit,
        onChromosomeEval: (index: Int, total: Int, command: String) -> Unit = { _, _, _ -> },
    ): Chromosome {
        var population = buildInitialPopulation(seedStrategies)
        var best: Chromosome = population.firstOrNull() ?: return emptyList()
        var bestFitness = 0.0
        var bestSuccessRate = 0.0
        var stagnationCount = 0
        val evalCache = HashMap<Chromosome, EvalResult>()

        val exitThreshold = (settings.maxGenerations / 2).coerceAtLeast(3)
        val injectThreshold = (exitThreshold / 2).coerceAtLeast(1)

        for (generation in 1..settings.maxGenerations) {
            if (!currentCoroutineContext().isActive) break

            val effectiveMutationRate = if (stagnationCount >= 2) {
                (settings.mutationRate * (1f + stagnationCount * 0.5f)).coerceAtMost(0.9f)
            } else {
                settings.mutationRate
            }

            val scored = evaluatePopulation(population, onChromosomeEval, evalCache)
            val fitnessPairs = scored.map { (ch, er) -> ch to er.fitness }
            val genBest = scored.maxByOrNull { it.second.fitness }
            if (genBest != null && genBest.second.fitness > bestFitness) {
                bestFitness = genBest.second.fitness
                bestSuccessRate = genBest.second.successRate
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
                    bestSuccessRate = bestSuccessRate,
                    population = fitnessPairs,
                    stagnationCount = stagnationCount,
                ),
            )
            memory?.save()
            fitnessCachePersistent?.save()
            if (bestFitness >= settings.targetFitness) break
            if (stagnationCount >= exitThreshold) break

            val survivors = evolver.tournament(fitnessPairs, settings.eliteCount, random = random)
            population = when {
                stagnationCount >= injectThreshold ->
                    buildSeedInjection(survivors, seedStrategies, effectiveMutationRate)
                stagnationCount >= 2 ->
                    buildDiverseGeneration(survivors, effectiveMutationRate)
                else ->
                    buildNextGeneration(survivors, effectiveMutationRate)
            }.withElite(best)
        }
        return best
    }

    private fun List<Chromosome>.withElite(elite: Chromosome): List<Chromosome> {
        if (isEmpty() || contains(elite)) return this
        return listOf(elite) + drop(1)
    }

    private fun buildInitialPopulation(seedStrategies: List<String>): List<Chromosome> {
        val shuffled = seedStrategies.shuffled(random)
        val fromSeeds = shuffled.take(settings.populationSize).map(::parseChromosome)
        val fillCount = (settings.populationSize - fromSeeds.size).coerceAtLeast(0)
        val fill = if (memory != null && memory.hasData()) {
            List(fillCount) { pool.weightedRandomChromosome(memory) }
        } else {
            List(fillCount) { pool.randomChromosome(random = random) }
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
            val child = evolver.crossover(p1, p2, random)
            offspring.add(evolver.mutate(child, mutationRate, random = random, memory = memory))
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

    private fun buildSeedInjection(
        elites: List<Chromosome>,
        seedStrategies: List<String>,
        mutationRate: Float,
    ): List<Chromosome> {
        val top = elites.take(2)
        val freshCount = settings.populationSize - top.size
        val fresh = seedStrategies.shuffled(random).take(freshCount).map(::parseChromosome)
        val fill = when {
            fresh.size >= freshCount -> fresh
            top.isNotEmpty() -> fresh + List(freshCount - fresh.size) {
                evolver.mutate(top[random.nextInt(top.size)], mutationRate, random = random, memory = memory)
            }
            else -> fresh + List(freshCount - fresh.size) { pool.randomChromosome(random = random) }
        }
        return top + fill
    }

    private suspend fun evaluatePopulation(
        population: List<Chromosome>,
        onChromosomeEval: (index: Int, total: Int, command: String) -> Unit = { _, _, _ -> },
        evalCache: MutableMap<Chromosome, EvalResult> = HashMap(),
    ): List<Pair<Chromosome, EvalResult>> {
        val results = population.mapIndexed { index, chromosome ->
            if (!currentCoroutineContext().isActive) return@mapIndexed chromosome to EvalResult(0.0, 0.0)
            onChromosomeEval(index, population.size, chromosome.toCommand())
            val evalResult = evalCache.getOrPut(chromosome) {
                val command = chromosome.toCommand()
                val persistedFitness = fitnessCachePersistent?.get(command)
                if (persistedFitness != null) {
                    EvalResult(fitness = persistedFitness, successRate = persistedFitness)
                } else {
                    val computed = evaluate(chromosome)
                    if (command.isNotBlank()) fitnessCachePersistent?.put(command, computed.fitness)
                    computed
                }
            }
            chromosome to evalResult
        }
        results.forEach { (chromosome, evalResult) ->
            memory?.record(chromosome.map { it.token }, evalResult.fitness)
        }
        return results
    }

    private suspend fun evaluate(chromosome: Chromosome): EvalResult {
        if (sites.isEmpty() || chromosome.isEmpty()) return EvalResult(0.0, 0.0)
        val command = chromosome.toCommand()
        val started = byeDpiEngine.start(
            config = EngineConfig.ByeDpi(args = command, socksPort = socksPort),
            upstream = Upstream.None,
        )
        if (started !is StartResult.Success) {
            runCatching { byeDpiEngine.stop() }
            return EvalResult(0.0, 0.0)
        }
        return try {
            val probe = probeFactory(socksPort, settings.timeoutMs)
            val semaphore = Semaphore(settings.concurrentProbes.coerceAtLeast(1))
            val probeResults = coroutineScope {
                sites.map { site ->
                    async {
                        semaphore.withPermit {
                            runCatching { probe.probe(site) }.getOrNull()
                        }
                    }
                }.map { it.await() }
            }
            val successCount = probeResults.count { it?.success == true }
            if (successCount == 0) return EvalResult(0.0, 0.0)
            val successRate = successCount.toDouble() / sites.size
            val avgLatencyMs = probeResults
                .filter { it?.success == true }
                .mapNotNull { it?.durationMs }
                .average()
            val fitness = successRate * (1.0 / (1.0 + avgLatencyMs / 2000.0))
            EvalResult(fitness = fitness, successRate = successRate)
        } finally {
            runCatching { byeDpiEngine.stop() }
        }
    }
}
