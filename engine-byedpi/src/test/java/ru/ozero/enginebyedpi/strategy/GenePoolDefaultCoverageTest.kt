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
}
