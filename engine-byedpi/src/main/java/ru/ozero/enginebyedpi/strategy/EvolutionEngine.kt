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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow
import kotlin.random.Random

class EvolutionEngine(
    private val byeDpiEngine: EnginePlugin,
    private val probeFactory: (socksPort: Int, timeoutMs: Long) -> SocksProbeClient,
    private val evolver: StrategyEvolver,
    private val pool: GenePool,
    private val sites: List<String>,
    private val settings: EvolutionSettings = EvolutionSettings(),
    private val memory: GeneMemory? = null,
    private val fitnessCachePersistent: StrategyFitnessCache? = null,
    private val random: Random = Random.Default,
) {

    data class EvolutionSettings(
        val populationSize: Int = 30,
        val maxGenerations: Int = 20,
        val mutationRate: Float = 0.2f,
        val eliteCount: Int = 3,
        val targetFitness: Double = 0.85,
        val concurrentProbes: Int = 10,
        val timeoutMs: Long = 5_000L,
        val initialSeedRatio: Double = 0.4,
        val initialMemoryRatio: Double = 0.3,
        val latencyClampMs: Double = 3_000.0,
        val successRateExponent: Double = 1.5,
        val portRotationBase: Int = 49_152,
        val portRotationRange: Int = 256,
    )

    data class GenerationResult(
        val generation: Int,
        val best: Chromosome,
        val bestFitness: Double,
        val bestSuccessRate: Double,
        val population: List<Pair<Chromosome, Double>>,
        val stagnationCount: Int = 0,
    )

    private data class EvalResult(
        val fitness: Double,
        val successRate: Double,
        val startFailed: Boolean = false,
    )

    private val portCounter = AtomicInteger(0)

    private fun nextRotatedSocksPort(): Int {
        val range = settings.portRotationRange.coerceAtLeast(1)
        return settings.portRotationBase + (portCounter.getAndIncrement() % range)
    }

    suspend fun evolve(
        seedStrategies: List<String>,
        onGeneration: (GenerationResult) -> Unit,
        onChromosomeEval: (index: Int, total: Int, command: String) -> Unit = { _, _, _ -> },
    ): Chromosome {
        var population = buildInitialPopulation(seedStrategies)
        var best: Chromosome = population.firstOrNull() ?: return emptyList()
        var bestFitness = -1.0
        var bestSuccessRate = 0.0
        var stagnationCount = 0
        val evalCache = HashMap<Chromosome, EvalResult>()

        val exitThreshold = (settings.maxGenerations / 2).coerceAtLeast(3)
        val injectThreshold = (exitThreshold / 2).coerceAtLeast(1)

        for (generation in 1..settings.maxGenerations) {
            if (!currentCoroutineContext().isActive) break

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
                    buildSeedInjection(survivors, seedStrategies, settings.mutationRate)
                stagnationCount >= 2 ->
                    buildDiverseGeneration(survivors, settings.mutationRate)
                else ->
                    buildNextGeneration(survivors, settings.mutationRate)
            }.withElite(best)
        }
        return best
    }

    internal fun computeFitness(successRate: Double, avgLatencyMs: Double): Double {
        if (successRate <= 0.0) return 0.0
        val clampedLatency = avgLatencyMs.coerceIn(0.0, settings.latencyClampMs)
        val latencyFactor = 1.0 - clampedLatency / settings.latencyClampMs
        return successRate.pow(settings.successRateExponent) * latencyFactor
    }

    internal fun computeProbeScore(result: ProbeResult?): Double {
        if (result == null) return 0.0
        if (result.success) return 1.0
        if (result.responseCode in 100..599) {
            return if (result.actualLength > 0L) SCORE_HTTP_PARTIAL else SCORE_HTTP_HEADERS
        }
        return 0.0
    }

    private fun List<Chromosome>.withElite(elite: Chromosome): List<Chromosome> {
        if (isEmpty() || contains(elite)) return this
        return listOf(elite) + drop(1)
    }

    private fun buildInitialPopulation(seedStrategies: List<String>): List<Chromosome> {
        val total = settings.populationSize.coerceAtLeast(1)
        val seedQuota = (total * settings.initialSeedRatio).toInt().coerceAtLeast(1).coerceAtMost(total)
        val memoryQuota = (total * settings.initialMemoryRatio).toInt().coerceAtMost(total - seedQuota)
        val randomQuota = (total - seedQuota - memoryQuota).coerceAtLeast(0)

        val seedPart = seedStrategies.shuffled(random).take(seedQuota).map(::parseChromosome)
        val seedDeficit = seedQuota - seedPart.size

        val memoryHasData = memory != null && memory.hasData()
        val memoryPart = List(memoryQuota + seedDeficit) {
            if (memoryHasData) {
                pool.weightedRandomChromosome(memory!!, random = random)
            } else {
                pool.randomChromosome(random = random)
            }
        }
        val randomPart = List(randomQuota) { pool.randomChromosome(random = random) }
        return seedPart + memoryPart + randomPart
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
                    if (command.isNotBlank() && !computed.startFailed) {
                        fitnessCachePersistent?.put(command, computed.fitness)
                    }
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
        val port = nextRotatedSocksPort()
        val started = byeDpiEngine.start(
            config = EngineConfig.ByeDpi(args = command, socksPort = port),
            upstream = Upstream.None,
        )
        if (started !is StartResult.Success) {
            return EvalResult(0.0, 0.0, startFailed = true)
        }
        return try {
            val probe = probeFactory(port, settings.timeoutMs)
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
            val probeScores = probeResults.map { computeProbeScore(it) }
            val avgScore = probeScores.average()
            if (avgScore <= 0.0) return EvalResult(0.0, 0.0)
            val successCount = probeResults.count { it?.success == true }
            val successRate = successCount.toDouble() / sites.size
            val avgLatencyMs = probeResults
                .filter { it?.success == true || (it?.responseCode ?: -1) in 100..599 }
                .mapNotNull { it?.durationMs }
                .let { if (it.isEmpty()) settings.latencyClampMs else it.average() }
            val fitness = computeFitness(avgScore, avgLatencyMs)
            EvalResult(fitness = fitness, successRate = successRate)
        } finally {
            runCatching { byeDpiEngine.stop() }
        }
    }

    private companion object {
        const val SCORE_HTTP_PARTIAL = 0.6
        const val SCORE_HTTP_HEADERS = 0.3
    }
}
