package ru.ozero.enginebyedpi.strategy

import kotlin.random.Random

class StrategyEvolver(private val pool: GenePool) {

    fun crossoverSinglePoint(
        parent1: Chromosome,
        parent2: Chromosome,
        random: Random = Random.Default,
    ): Chromosome {
        if (parent1.isEmpty() || parent2.isEmpty()) return parent1.ifEmpty { parent2 }
        val blocks1 = blocks(parent1)
        val blocks2 = blocks(parent2)
        val splitPoint = random.nextInt(minOf(blocks1.size, blocks2.size).coerceAtLeast(1))
        return ByeDpiOptionBlocks.flatten(blocks1.subList(0, splitPoint) + blocks2.subList(splitPoint, blocks2.size))
    }

    fun crossoverTwoPoint(
        parent1: Chromosome,
        parent2: Chromosome,
        random: Random = Random.Default,
    ): Chromosome {
        if (parent1.isEmpty() || parent2.isEmpty()) return parent1.ifEmpty { parent2 }
        val blocks1 = blocks(parent1)
        val blocks2 = blocks(parent2)
        val minLen = minOf(blocks1.size, blocks2.size)
        if (minLen < 2) return crossoverSinglePoint(parent1, parent2, random)
        val rawA = random.nextInt(minLen)
        val rawB = random.nextInt(minLen)
        val i = minOf(rawA, rawB)
        val j = (maxOf(rawA, rawB) + 1).coerceAtMost(minLen)
        return ByeDpiOptionBlocks.flatten(
            blocks1.subList(0, i) +
                blocks2.subList(i, j.coerceAtMost(blocks2.size)) +
                blocks1.subList(j.coerceAtMost(blocks1.size), blocks1.size),
        )
    }

    fun crossoverUniform(
        parent1: Chromosome,
        parent2: Chromosome,
        random: Random = Random.Default,
    ): Chromosome {
        if (parent1.isEmpty()) return parent2
        if (parent2.isEmpty()) return parent1
        val blocks1 = blocks(parent1)
        val blocks2 = blocks(parent2)
        val maxLen = maxOf(blocks1.size, blocks2.size)
        return ByeDpiOptionBlocks.flatten(
            (0 until maxLen).map { i ->
                when {
                    i < blocks1.size && i < blocks2.size -> if (random.nextBoolean()) blocks1[i] else blocks2[i]
                    i < blocks1.size -> blocks1[i]
                    else -> blocks2[i]
                }
            },
        )
    }

    fun crossover(
        parent1: Chromosome,
        parent2: Chromosome,
        random: Random = Random.Default,
    ): Chromosome {
        if (parent1.isEmpty() || parent2.isEmpty()) return parent1.ifEmpty { parent2 }
        return when (random.nextInt(3)) {
            0 -> crossoverSinglePoint(parent1, parent2, random)
            1 -> crossoverTwoPoint(parent1, parent2, random)
            else -> crossoverUniform(parent1, parent2, random)
        }
    }

    fun insert(chromosome: Chromosome, random: Random = Random.Default): Chromosome {
        val blocks = blocks(chromosome).toMutableList()
        val geneBlock = pool.randomBlock(random)
        if (blocks.isEmpty()) return geneBlock
        val pos = random.nextInt(blocks.size + 1)
        blocks.add(pos, geneBlock)
        return ByeDpiOptionBlocks.flatten(blocks)
    }

    fun delete(chromosome: Chromosome, random: Random = Random.Default): Chromosome {
        val blocks = blocks(chromosome).toMutableList()
        if (blocks.size <= 1) return chromosome
        val pos = random.nextInt(blocks.size)
        blocks.removeAt(pos)
        return ByeDpiOptionBlocks.flatten(blocks)
    }

    fun swap(chromosome: Chromosome, random: Random = Random.Default): Chromosome {
        val blocks = blocks(chromosome).toMutableList()
        if (blocks.size < 2) return chromosome
        val i = random.nextInt(blocks.size)
        val j = (random.nextInt(blocks.size - 1)).let { if (it >= i) it + 1 else it }
        return ByeDpiOptionBlocks.flatten(
            blocks.also { list ->
                val tmp = list[i]
                list[i] = list[j]
                list[j] = tmp
            },
        )
    }

    fun mutate(
        chromosome: Chromosome,
        rate: Float = 0.2f,
        random: Random = Random.Default,
        memory: GeneMemory? = null,
    ): Chromosome {
        if (chromosome.isEmpty()) return chromosome
        val resultBlocks: List<Chromosome> = blocks(chromosome).map { block ->
            if (random.nextFloat() < rate) {
                if (memory != null && memory.hasData()) {
                    pool.weightedRandomBlock(memory, random)
                } else {
                    pool.randomBlock(random)
                }
            } else {
                block
            }
        }
        var result = ByeDpiOptionBlocks.flatten(resultBlocks)
        if (random.nextFloat() < rate / 2) result = insert(result, random)
        if (result.size > 3 && random.nextFloat() < rate / 2) result = delete(result, random)
        if (result.size >= 2 && random.nextFloat() < rate / 2) result = swap(result, random)
        return result
    }

    fun select(
        scored: List<Pair<Chromosome, Double>>,
        k: Int,
    ): List<Chromosome> =
        scored.sortedByDescending { it.second }
            .take(k)
            .map { it.first }

    fun tournament(
        scored: List<Pair<Chromosome, Double>>,
        k: Int,
        tournamentSize: Int = 3,
        random: Random = Random.Default,
    ): List<Chromosome> {
        if (scored.isEmpty()) return emptyList()
        val effectiveSize = tournamentSize.coerceAtMost(scored.size)
        val result = mutableListOf<Chromosome>()
        repeat(k.coerceAtMost(scored.size)) {
            val contenders = scored.indices.shuffled(random).take(effectiveSize)
            val winner = contenders.maxByOrNull { scored[it].second } ?: contenders.first()
            result.add(scored[winner].first)
        }
        return result
    }

    private fun blocks(chromosome: Chromosome): List<Chromosome> =
        ByeDpiOptionBlocks.blocks(chromosome).ifEmpty { listOf(chromosome) }
}
