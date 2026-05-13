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
    fun `crossover uses aligned split point — no interleaving of parent genes`() {
        val p1 = parseChromosome("-a -b -c -d")
        val p2 = parseChromosome("-w -x -y -z")
        val p1Tokens = p1.map { it.token }.toSet()
        val p2Tokens = p2.map { it.token }.toSet()
        repeat(20) { seed ->
            val child = evolver.crossover(p1, p2, Random(seed))
            val p1Part = child.takeWhile { it.token in p1Tokens }
            val p2Part = child.dropWhile { it.token in p1Tokens }
            assertTrue(
                p2Part.all { it.token in p2Tokens },
                "seed=$seed: tail should be only p2 genes, got $child",
            )
            assertTrue(
                p1Part.all { it.token in p1Tokens },
                "seed=$seed: head should be only p1 genes, got $child",
            )
        }
    }

    @Test
    fun `crossover length equals sum of used parts from both parents`() {
        val p1 = parseChromosome("-a -b -c -d")
        val p2 = parseChromosome("-x -y -z")
        repeat(10) { seed ->
            val child = evolver.crossover(p1, p2, Random(seed))
            assertTrue(child.size >= 1, "child must not be empty, seed=$seed")
            assertTrue(child.size <= p1.size + p2.size, "child too long, seed=$seed: $child")
        }
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
    fun `mutate with memory uses weighted gene selection`() {
        val memory = GeneMemory(java.io.File.createTempFile("mem", ".json").also { it.deleteOnExit() })
        memory.record(listOf("-a"), fitness = 1.0)
        val chromosome = parseChromosome("-a -b -c")
        val vocab = pool.allGenes().map { it.token }.toSet()
        val mutated = evolver.mutate(chromosome, rate = 1f, random = kotlin.random.Random(42), memory = memory)
        assertEquals(chromosome.size, mutated.size)
        mutated.forEach { gene ->
            assertTrue(vocab.contains(gene.token), "mutated gene '${gene.token}' not in vocab")
        }
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

    @Test
    fun `tournament returns k chromosomes`() {
        val scored = listOf(
            parseChromosome("-a") to 0.5,
            parseChromosome("-b") to 1.0,
            parseChromosome("-c") to 0.2,
            parseChromosome("-d") to 0.8,
            parseChromosome("-e") to 0.6,
        )
        val selected = evolver.tournament(scored, k = 3, random = Random(42))
        assertEquals(3, selected.size)
    }

    @Test
    fun `tournament with empty list returns empty`() {
        assertEquals(emptyList(), evolver.tournament(emptyList(), k = 3))
    }

    @Test
    fun `tournament k greater than list size is clamped`() {
        val scored = listOf(
            parseChromosome("-a") to 0.5,
            parseChromosome("-b") to 1.0,
        )
        val selected = evolver.tournament(scored, k = 10, random = Random(0))
        assertEquals(2, selected.size)
    }

    @Test
    fun `tournament favors higher fitness chromosomes`() {
        val winner = parseChromosome("-best")
        val scored = listOf(
            parseChromosome("-bad1") to 0.0,
            parseChromosome("-bad2") to 0.0,
            parseChromosome("-bad3") to 0.0,
            parseChromosome("-bad4") to 0.0,
            winner to 1.0,
        )
        val counts = mutableMapOf<Chromosome, Int>()
        repeat(50) {
            val picked = evolver.tournament(scored, k = 1, tournamentSize = 3, random = Random(it))
            counts[picked[0]] = (counts[picked[0]] ?: 0) + 1
        }
        val winnerCount = counts[winner] ?: 0
        assertTrue(winnerCount > 10, "winner should be selected frequently, got $winnerCount/50")
    }

    @Test
    fun `insert adds one gene to chromosome`() {
        val chromosome = parseChromosome("-a -b -c")
        val result = evolver.insert(chromosome, Random(0))
        assertEquals(chromosome.size + 1, result.size)
    }

    @Test
    fun `insert on empty chromosome returns single gene`() {
        val result = evolver.insert(emptyList(), Random(0))
        assertEquals(1, result.size)
    }

    @Test
    fun `delete removes one gene from chromosome`() {
        val chromosome = parseChromosome("-a -b -c -d")
        val result = evolver.delete(chromosome, Random(0))
        assertEquals(chromosome.size - 1, result.size)
    }

    @Test
    fun `delete on single gene chromosome returns unchanged`() {
        val chromosome = parseChromosome("-a")
        val result = evolver.delete(chromosome, Random(0))
        assertEquals(chromosome, result)
    }

    @Test
    fun `mutate with rate 1 may change chromosome length via insert or delete`() {
        val chromosome = parseChromosome("-a -b -c -d -e -f -g -h")
        val results = (1..50).map { evolver.mutate(chromosome, rate = 1f, random = Random(it)) }
        assertTrue(results.any { it.size != chromosome.size }, "at least one mutation should change length")
    }
}
