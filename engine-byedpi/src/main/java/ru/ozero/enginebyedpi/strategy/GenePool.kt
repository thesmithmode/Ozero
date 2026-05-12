package ru.ozero.enginebyedpi.strategy

import kotlin.random.Random

class GenePool(seedStrategies: List<String>) {

    private val vocabulary: List<StrategyGene>

    init {
        vocabulary = seedStrategies
            .flatMap { cmd -> cmd.split(" ").filter(String::isNotBlank) }
            .distinct()
            .map(::StrategyGene)
            .ifEmpty { listOf(StrategyGene("-K")) }
    }

    fun randomGene(random: Random = Random.Default): StrategyGene =
        vocabulary[random.nextInt(vocabulary.size)]

    fun weightedRandomGene(memory: GeneMemory, random: Random = Random.Default): StrategyGene {
        val weights = vocabulary.map { gene -> memory.ucbScore(gene.token).coerceAtLeast(0.01) }
        val total = weights.sum()
        var cursor = random.nextDouble() * total
        for (i in weights.indices) {
            cursor -= weights[i]
            if (cursor <= 0.0) return vocabulary[i]
        }
        return vocabulary.last()
    }

    fun randomChromosome(
        length: IntRange = 3..12,
        random: Random = Random.Default,
    ): Chromosome {
        val size = random.nextInt(length.first, length.last + 1)
        return List(size) { randomGene(random) }
    }

    fun weightedRandomChromosome(
        memory: GeneMemory,
        length: IntRange = 3..12,
        random: Random = Random.Default,
    ): Chromosome {
        val size = random.nextInt(length.first, length.last + 1)
        return List(size) { weightedRandomGene(memory, random) }
    }

    fun vocabularySize(): Int = vocabulary.size

    fun allGenes(): List<StrategyGene> = vocabulary.toList()
}
