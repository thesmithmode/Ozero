package ru.ozero.enginebyedpi.strategy

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenePoolTest {

    private val seeds = listOf("-Ku -An -d4", "-s5 -f0x81 -K", "-An -d2 -s3")

    @Test
    fun `vocabulary built from unique tokens across all seeds`() {
        val pool = GenePool(seeds)
        assertTrue(pool.vocabularySize() > 1)
        val genes = pool.allGenes().map { it.token }
        assertTrue(genes.contains("-Ku"), "flag -Ku in vocabulary")
        assertTrue(genes.contains("-An"), "flag -An in vocabulary")
        assertTrue(genes.contains("-d4"), "value token in vocabulary")
        assertTrue(genes.contains("-K"), "flag -K in vocabulary")
    }

    @Test
    fun `vocabulary has no duplicates`() {
        val pool = GenePool(seeds)
        val tokens = pool.allGenes().map { it.token }
        assertEquals(tokens.distinct(), tokens)
    }

    @Test
    fun `randomGene returns gene from vocabulary`() {
        val pool = GenePool(seeds)
        val vocab = pool.allGenes().map { it.token }.toSet()
        repeat(20) {
            val gene = pool.randomGene(Random(it.toLong()))
            assertTrue(vocab.contains(gene.token), "gene '${gene.token}' not in vocabulary")
        }
    }

    @Test
    fun `randomChromosome length within specified range`() {
        val pool = GenePool(seeds)
        repeat(20) {
            val chromosome = pool.randomChromosome(3..8, Random(it.toLong()))
            assertTrue(chromosome.size in 3..8, "length ${chromosome.size} out of range 3..8")
        }
    }

    @Test
    fun `parseChromosome and toCommand round-trip`() {
        val command = "-Ku -An -d4"
        val chromosome = parseChromosome(command)
        assertEquals(3, chromosome.size)
        assertEquals("-Ku", chromosome[0].token)
        assertEquals("-An", chromosome[1].token)
        assertEquals("-d4", chromosome[2].token)
        assertEquals(command, chromosome.toCommand())
    }

    @Test
    fun `empty seed list uses fallback vocabulary`() {
        val pool = GenePool(emptyList())
        assertTrue(pool.vocabularySize() >= 1)
        val gene = pool.randomGene()
        assertFalse(gene.token.isBlank())
    }

    @Test
    fun `parseChromosome ignores extra spaces`() {
        val chromosome = parseChromosome("  -Ku  -An  ")
        assertEquals(2, chromosome.size)
    }

    @Test
    fun `weightedRandomGene returns gene from vocabulary`() {
        val pool = GenePool(seeds)
        val memory = GeneMemory(java.io.File.createTempFile("mem", ".json").also { it.deleteOnExit() })
        memory.record(listOf("-Ku"), fitness = 1.0)
        val vocab = pool.allGenes().map { it.token }.toSet()
        repeat(20) {
            val gene = pool.weightedRandomGene(memory, kotlin.random.Random(it.toLong()))
            assertTrue(vocab.contains(gene.token), "weighted gene '${gene.token}' not in vocab")
        }
    }

    @Test
    fun `weightedRandomChromosome length within range`() {
        val pool = GenePool(seeds)
        val memory = GeneMemory(java.io.File.createTempFile("mem2", ".json").also { it.deleteOnExit() })
        memory.record(listOf("-An"), fitness = 0.8)
        repeat(20) {
            val chromosome = pool.weightedRandomChromosome(memory, 3..8, kotlin.random.Random(it.toLong()))
            assertTrue(chromosome.size in 3..8, "length ${chromosome.size} out of range 3..8")
        }
    }
}
