package ru.ozero.enginebyedpi.strategy

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrategyEvolverTest {

    private lateinit var pool: GenePool
    private lateinit var evolver: StrategyEvolver

    @BeforeEach
    fun setUp() {
        pool = GenePool(listOf("-a -b -c -d", "-e -f -g -h", "-i -j -k -l"))
        evolver = StrategyEvolver(pool)
    }

    @Test
    fun `crossover produces chromosome from both parents`() {
        val p1 = parseChromosome("-a -b -c")
        val p2 = parseChromosome("-x -y -z")
        val child = evolver.crossover(p1, p2, Random(42))
        assertTrue(child.isNotEmpty())
    }

    @Test
    fun `crossover with empty parent1 returns parent2`() {
        val result = evolver.crossover(emptyList(), parseChromosome("-a"), Random(0))
        assertEquals(parseChromosome("-a"), result)
    }

    @Test
    fun `crossover with empty parent2 returns parent1`() {
        val p1 = parseChromosome("-a -b")
        val result = evolver.crossover(p1, emptyList(), Random(0))
        assertEquals(p1, result)
    }

    @Test
    fun `mutate with rate 0 returns identical chromosome`() {
        val chromosome = parseChromosome("-a -b -c -d")
        val mutated = evolver.mutate(chromosome, rate = 0f, random = Random(0))
        assertEquals(chromosome, mutated)
    }

    @Test
    fun `mutate with rate 1 replaces all genes from pool`() {
        val chromosome = parseChromosome("-a -b -c")
        val vocabTokens = pool.allGenes().map { it.token }.toSet()
        val mutated = evolver.mutate(chromosome, rate = 1f, random = Random(42))
        assertEquals(chromosome.size, mutated.size)
        mutated.forEach { gene ->
            assertTrue(vocabTokens.contains(gene.token), "mutated gene '${gene.token}' not in vocab")
        }
    }

    @Test
    fun `mutate empty chromosome returns empty`() {
        assertEquals(emptyList(), evolver.mutate(emptyList()))
    }

    @Test
    fun `select returns top-k by fitness descending`() {
        val scored = listOf(
            parseChromosome("-a") to 0.5,
            parseChromosome("-b") to 1.0,
            parseChromosome("-c") to 0.2,
            parseChromosome("-d") to 0.8,
        )
        val selected = evolver.select(scored, k = 2)
        assertEquals(2, selected.size)
        assertEquals(parseChromosome("-b"), selected[0])
        assertEquals(parseChromosome("-d"), selected[1])
    }

    @Test
    fun `select k greater than list size returns all sorted`() {
        val scored = listOf(
            parseChromosome("-a") to 0.3,
            parseChromosome("-b") to 0.9,
        )
        val selected = evolver.select(scored, k = 10)
        assertEquals(2, selected.size)
        assertEquals(parseChromosome("-b"), selected[0])
    }

    @Test
    fun `select empty list returns empty`() {
        assertEquals(emptyList(), evolver.select(emptyList(), k = 5))
    }
}
