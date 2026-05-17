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
        length: IntRange = 5..12,
        random: Random = Random.Default,
    ): Chromosome {
        val size = random.nextInt(length.first, length.last + 1)
        return List(size) { randomGene(random) }
    }

    fun weightedRandomChromosome(
        memory: GeneMemory,
        length: IntRange = 5..12,
        random: Random = Random.Default,
    ): Chromosome {
        val size = random.nextInt(length.first, length.last + 1)
        return List(size) { weightedRandomGene(memory, random) }
    }

    fun randomSubsequence(
        sourceCommands: List<String>,
        minLen: Int = 2,
        maxLen: Int = 5,
        random: Random = Random.Default,
    ): Chromosome {
        if (sourceCommands.isEmpty()) return randomChromosome(minLen..maxLen, random)
        val source = sourceCommands[random.nextInt(sourceCommands.size)]
        val tokens = source.split(" ").filter(String::isNotBlank)
        if (tokens.size <= minLen) return tokens.map(::StrategyGene)
        val start = random.nextInt(tokens.size - minLen + 1)
        val actualMax = minOf(maxLen, tokens.size - start)
        val len = random.nextInt(minLen, actualMax + 1)
        return tokens.subList(start, start + len).map(::StrategyGene)
    }

    fun vocabularySize(): Int = vocabulary.size

    fun allGenes(): List<StrategyGene> = vocabulary.toList()
}
