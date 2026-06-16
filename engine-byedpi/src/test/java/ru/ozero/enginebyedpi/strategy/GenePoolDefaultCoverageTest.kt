package ru.ozero.enginebyedpi.strategy

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenePoolDefaultCoverageTest {

    @Test
    fun `default random methods produce genes blocks and chromosomes`() {
        val pool = GenePool(listOf("-a 1 -b 2", "-c -d"))

        assertTrue(pool.randomGene().token.isNotBlank())
        assertTrue(pool.randomBlock().isNotEmpty())
        assertTrue(pool.randomChromosome().isNotEmpty())
    }

    @Test
    fun `weighted default methods use memory and preserve known genes`() {
        val pool = GenePool(listOf("-a 1 -b 2", "-c -d"))
        val memory = GeneMemory(File.createTempFile("gene-pool", ".json").also { it.deleteOnExit() })
        repeat(20) { memory.record(listOf("-a"), fitness = 1.0) }
        val known = pool.allGenes().map { it.token }.toSet()

        val gene = pool.weightedRandomGene(memory)
        val block = pool.weightedRandomBlock(memory)
        val chromosome = pool.weightedRandomChromosome(memory)

        assertTrue(gene.token in known)
        assertTrue(block.isNotEmpty())
        assertTrue(chromosome.isNotEmpty())
        assertTrue(chromosome.all { it.token in known || it.token.toIntOrNull() != null })
    }

    @Test
    fun `blank seed falls back to K gene and subsequence falls back for empty input`() {
        val pool = GenePool(listOf("", "   "))

        assertEquals(1, pool.vocabularySize())
        assertEquals("-K", pool.randomGene(Random(0)).token)
        assertTrue(pool.randomSubsequence(emptyList()).isNotEmpty())
    }

    @Test
    fun `randomSubsequence default bounds handles short source`() {
        val pool = GenePool(listOf("-a"))

        val result = pool.randomSubsequence(listOf("-a"))

        assertEquals(parseChromosome("-a"), result)
    }

    @Test
    fun `randomChromosome falls back to one block when bounds reject growth`() {
        val pool = GenePool(listOf("-a 1 -d 2"))

        val result = pool.randomChromosome(length = 1..1, random = Random(0))

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `weightedRandomChromosome falls back to weighted block when bounds reject growth`() {
        val pool = GenePool(listOf("-a 1 -d 2"))
        val memory = GeneMemory(File.createTempFile("gene-pool-weighted", ".json").also { it.deleteOnExit() })

        val result = pool.weightedRandomChromosome(memory, length = 1..1, random = Random(1))

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `randomSubsequence falls back when selected source has no option blocks`() {
        val pool = GenePool(listOf("-K"))

        val result = pool.randomSubsequence(listOf("plain-token"), random = Random(2))

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `randomSubsequence can stop after minimum length and stays inside max`() {
        val pool = GenePool(listOf("-a 1 -d 2 -s 3 -t 4"))

        val result = pool.randomSubsequence(
            sourceCommands = listOf("-a 1 -d 2 -s 3 -t 4"),
            minLen = 2,
            maxLen = 3,
            random = Random(3),
        )

        assertTrue(result.size in 2..3)
    }

    @Test
    fun `empty and blank seeds use fallback command blocks`() {
        val empty = GenePool(emptyList())
        val blank = GenePool(listOf(" ", "\t"))

        assertEquals("-K", empty.randomBlock(Random(0)).single().token)
        assertEquals("-K", blank.randomBlock(Random(0)).single().token)
    }

    @Test
    fun `weighted random methods return known values for untrained memory`() {
        val pool = GenePool(listOf("-a 1", "-d 2"))
        val memory = GeneMemory(File.createTempFile("gene-pool-tail", ".json").also { it.deleteOnExit() })
        val known = pool.allGenes().map { it.token }.toSet()

        assertTrue(pool.weightedRandomGene(memory, Random(9)).token in known)
        assertTrue(pool.weightedRandomBlock(memory, Random(9)).all { it.token in known })
    }

    @Test
    fun `randomSubsequence clamps long minimum request to available source`() {
        val pool = GenePool(listOf("-a 1 -d 2"))

        val result = pool.randomSubsequence(
            sourceCommands = listOf("-a 1 -d 2"),
            minLen = 10,
            maxLen = 10,
            random = Random(4),
        )

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `gene pool fallback and weighted tails stay non empty`() {
        val pool = GenePool(listOf("plain", "", "-a 1"))
        val memory = GeneMemory(File.createTempFile("gene-pool-more", ".json").also { it.deleteOnExit() })

        repeat(20) {
            assertTrue(pool.randomChromosome(length = 2..3, random = Random(it)).isNotEmpty())
            assertTrue(pool.weightedRandomChromosome(memory, length = 2..3, random = Random(it)).isNotEmpty())
            assertTrue(pool.randomSubsequence(listOf("-a 1 -d 2 -s 3"), 1, 2, Random(it)).isNotEmpty())
        }
    }

    @Test
    fun `random subsequence covers viable starts and stop branches`() {
        val pool = GenePool(listOf("-a 1 -d 2 -s 3 -t 4 -q 5"))

        repeat(40) { seed ->
            val result = pool.randomSubsequence(
                sourceCommands = listOf("-a 1 -d 2 -s 3 -t 4 -q 5"),
                minLen = 3,
                maxLen = 6,
                random = Random(seed),
            )

            assertTrue(result.size in 3..6)
        }
    }
}
