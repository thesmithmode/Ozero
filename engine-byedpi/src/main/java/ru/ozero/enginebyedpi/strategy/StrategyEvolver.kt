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

    fun mutate(
        chromosome: Chromosome,
        rate: Float = 0.2f,
        random: Random = Random.Default,
        memory: GeneMemory? = null,
    ): Chromosome {
        if (chromosome.isEmpty()) return chromosome
        return chromosome.map { gene ->
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
    }

    fun select(
        scored: List<Pair<Chromosome, Double>>,
        k: Int,
    ): List<Chromosome> =
        scored.sortedByDescending { it.second }
            .take(k)
            .map { it.first }
}
