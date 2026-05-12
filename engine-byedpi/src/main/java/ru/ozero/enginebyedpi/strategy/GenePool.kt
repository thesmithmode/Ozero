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

    fun randomChromosome(
        length: IntRange = 3..12,
        random: Random = Random.Default,
    ): Chromosome {
        val size = random.nextInt(length.first, length.last + 1)
        return List(size) { randomGene(random) }
    }

    fun vocabularySize(): Int = vocabulary.size

    fun allGenes(): List<StrategyGene> = vocabulary.toList()
}
