package ru.ozero.enginebyedpi.strategy

import kotlin.random.Random

class StrategyEvolver(private val pool: GenePool) {

    fun crossover(
        parent1: Chromosome,
        parent2: Chromosome,
        random: Random = Random.Default,
    ): Chromosome {
        if (parent1.isEmpty() || parent2.isEmpty()) return parent1.ifEmpty { parent2 }
        val splitPoint = random.nextInt(parent1.size)
        return parent1.subList(0, splitPoint) + parent2.subList(
            (random.nextInt(parent2.size)),
            parent2.size,
        )
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
        return result
    }

    fun select(
        scored: List<Pair<Chromosome, Double>>,
        k: Int,
    ): List<Chromosome> =
        scored.sortedByDescending { it.second }
            .take(k)
            .map { it.first }
}
