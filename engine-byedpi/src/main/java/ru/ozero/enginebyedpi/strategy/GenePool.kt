package ru.ozero.enginebyedpi.strategy

import kotlin.random.Random

class GenePool(seedStrategies: List<String>) {

    private val vocabulary: List<StrategyGene>
    private val optionBlocks: List<Chromosome>
    private val sourceCommands: List<String>

    init {
        sourceCommands = seedStrategies.filter { it.isNotBlank() }.ifEmpty { listOf("-K") }
        vocabulary = seedStrategies
            .flatMap { cmd -> ByeDpiOptionBlocks.tokenize(cmd) }
            .distinct()
            .map(::StrategyGene)
            .ifEmpty { listOf(StrategyGene("-K")) }
        optionBlocks = sourceCommands
            .flatMap { cmd -> ByeDpiOptionBlocks.commandBlocks(cmd) }
            .filter { it.isNotEmpty() }
            .distinct()
            .ifEmpty { listOf(listOf(StrategyGene("-K"))) }
    }

    fun randomGene(random: Random = Random.Default): StrategyGene =
        vocabulary[random.nextInt(vocabulary.size)]

    fun weightedRandomGene(memory: GeneMemory, random: Random = Random.Default): StrategyGene {
        val weights = vocabulary.map { gene -> memory.sampleScore(gene.token, random).coerceAtLeast(0.01) }
        val total = weights.sum()
        var cursor = random.nextDouble() * total
        for (i in weights.indices) {
            cursor -= weights[i]
            if (cursor <= 0.0) return vocabulary[i]
        }
        return vocabulary.last()
    }

    fun randomBlock(random: Random = Random.Default): Chromosome =
        optionBlocks[random.nextInt(optionBlocks.size)]

    fun weightedRandomBlock(memory: GeneMemory, random: Random = Random.Default): Chromosome {
        val weights = optionBlocks.map { block ->
            block.map { gene -> memory.sampleScore(gene.token, random).coerceAtLeast(0.01) }.average()
        }
        val total = weights.sum()
        var cursor = random.nextDouble() * total
        for (i in weights.indices) {
            cursor -= weights[i]
            if (cursor <= 0.0) return optionBlocks[i]
        }
        return optionBlocks.last()
    }

    fun randomChromosome(
        length: IntRange = 5..12,
        random: Random = Random.Default,
    ): Chromosome {
        val size = random.nextInt(length.first, length.last + 1)
        val blocks = mutableListOf<Chromosome>()
        while (blocks.sumOf { it.size } < size) {
            val next = randomBlock(random)
            val currentSize = blocks.sumOf { it.size }
            if (currentSize >= length.first && currentSize + next.size > length.last) break
            blocks += next
        }
        return ByeDpiOptionBlocks.flatten(blocks).ifEmpty { randomBlock(random) }
    }

    fun weightedRandomChromosome(
        memory: GeneMemory,
        length: IntRange = 5..12,
        random: Random = Random.Default,
    ): Chromosome {
        val size = random.nextInt(length.first, length.last + 1)
        val blocks = mutableListOf<Chromosome>()
        while (blocks.sumOf { it.size } < size) {
            val next = weightedRandomBlock(memory, random)
            val currentSize = blocks.sumOf { it.size }
            if (currentSize >= length.first && currentSize + next.size > length.last) break
            blocks += next
        }
        return ByeDpiOptionBlocks.flatten(blocks).ifEmpty { weightedRandomBlock(memory, random) }
    }

    fun randomSubsequence(
        sourceCommands: List<String>,
        minLen: Int = 2,
        maxLen: Int = 5,
        random: Random = Random.Default,
    ): Chromosome {
        if (sourceCommands.isEmpty()) return randomChromosome(minLen..maxLen, random)
        val source = sourceCommands[random.nextInt(sourceCommands.size)]
        val blocks = ByeDpiOptionBlocks.commandBlocks(source)
        if (blocks.isEmpty()) return randomChromosome(minLen..maxLen, random)
        if (blocks.sumOf { it.size } <= minLen) return ByeDpiOptionBlocks.flatten(blocks)
        val viableStarts = blocks.indices
            .filter { start -> blocks.drop(start).sumOf { it.size } >= minLen }
            .ifEmpty { listOf(0) }
        val start = viableStarts[random.nextInt(viableStarts.size)]
        val selected = mutableListOf<Chromosome>()
        var tokenCount = 0
        for (block in blocks.drop(start)) {
            if (tokenCount >= minLen && tokenCount + block.size > maxLen) break
            selected += block
            tokenCount += block.size
            if (tokenCount >= minLen && random.nextBoolean()) break
        }
        return ByeDpiOptionBlocks.flatten(selected.ifEmpty { listOf(blocks[start]) })
    }

    fun vocabularySize(): Int = vocabulary.size

    fun allGenes(): List<StrategyGene> = vocabulary.toList()
}
