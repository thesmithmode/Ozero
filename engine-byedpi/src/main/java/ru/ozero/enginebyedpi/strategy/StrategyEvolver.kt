package ru.ozero.enginebyedpi.strategy

import kotlin.random.Random

class StrategyEvolver(private val pool: GenePool) {

    fun crossoverSinglePoint(
        parent1: Chromosome,
        parent2: Chromosome,
        random: Random = Random.Default,
    ): Chromosome {
        if (parent1.isEmpty() || parent2.isEmpty()) return parent1.ifEmpty { parent2 }
        val splitPoint = random.nextInt(minOf(parent1.size, parent2.size).coerceAtLeast(1))
        return parent1.subList(0, splitPoint) + parent2.subList(splitPoint, parent2.size)
    }

    fun crossoverTwoPoint(
        parent1: Chromosome,
        parent2: Chromosome,
        random: Random = Random.Default,
    ): Chromosome {
        if (parent1.isEmpty() || parent2.isEmpty()) return parent1.ifEmpty { parent2 }
        val minLen = minOf(parent1.size, parent2.size)
        if (minLen < 2) return crossoverSinglePoint(parent1, parent2, random)
        val rawA = random.nextInt(minLen)
        val rawB = random.nextInt(minLen)
        val i = minOf(rawA, rawB)
        val j = (maxOf(rawA, rawB) + 1).coerceAtMost(minLen)
        return parent1.subList(0, i) +
            parent2.subList(i, j.coerceAtMost(parent2.size)) +
            parent1.subList(j.coerceAtMost(parent1.size), parent1.size)
    }

    fun crossoverUniform(
        parent1: Chromosome,
        parent2: Chromosome,
        random: Random = Random.Default,
    ): Chromosome {
        if (parent1.isEmpty()) return parent2
        if (parent2.isEmpty()) return parent1
        val maxLen = maxOf(parent1.size, parent2.size)
        return (0 until maxLen).map { i ->
            when {
                i < parent1.size && i < parent2.size -> if (random.nextBoolean()) parent1[i] else parent2[i]
                i < parent1.size -> parent1[i]
                else -> parent2[i]
            }
        }
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
        val gene = pool.randomGene(random)
        if (chromosome.isEmpty()) return listOf(gene)
        val pos = random.nextInt(chromosome.size + 1)
        return chromosome.subList(0, pos) + listOf(gene) + chromosome.subList(pos, chromosome.size)
    }

    fun delete(chromosome: Chromosome, random: Random = Random.Default): Chromosome {
        if (chromosome.size <= 1) return chromosome
        val pos = random.nextInt(chromosome.size)
        return chromosome.filterIndexed { i, _ -> i != pos }
    }

    fun swap(chromosome: Chromosome, random: Random = Random.Default): Chromosome {
        if (chromosome.size < 2) return chromosome
        val i = random.nextInt(chromosome.size)
        val j = (random.nextInt(chromosome.size - 1)).let { if (it >= i) it + 1 else it }
        return chromosome.toMutableList().also { list ->
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
    }

    fun mutate(
        chromosome: Chromosome,
        rate: Float = 0.2f,
        random: Random = Random.Default,
        memory: GeneMemory? = null,
    ): Chromosome {
        if (chromosome.isEmpty()) return chromosome
        var result: Chromosome = chromosome.map { gene ->
            if (random.nextFloat() < rate) {
                if (memory != null && memory.hasData()) {
                    pool.weightedRandomGene(memory, random)
                } else {
                    pool.randomGene(random)
                }
            } else {
                gene
            }
        }
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
}
