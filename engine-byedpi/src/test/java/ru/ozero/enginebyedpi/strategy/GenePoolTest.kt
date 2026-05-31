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
    fun `default randomChromosome min length is 5`() {
        val pool = GenePool(seeds)
        repeat(50) {
            val chromosome = pool.randomChromosome(random = Random(it.toLong()))
            assertTrue(chromosome.size >= 5, "default min length must be 5, got ${chromosome.size}")
        }
    }

    @Test
    fun `default weightedRandomChromosome min length is 5`() {
        val pool = GenePool(seeds)
        val memory = GeneMemory(java.io.File.createTempFile("mem3", ".json").also { it.deleteOnExit() })
        memory.record(listOf("-An"), fitness = 0.8)
        repeat(50) {
            val chromosome = pool.weightedRandomChromosome(memory, random = Random(it.toLong()))
            assertTrue(chromosome.size >= 5, "default min length must be 5, got ${chromosome.size}")
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

    @Test
    fun `randomSubsequence length within specified range`() {
        val pool = GenePool(seeds)
        val sources = listOf("-a -b -c -d -e -f -g -h -i -j")
        repeat(30) {
            val chromosome = pool.randomSubsequence(sources, minLen = 2, maxLen = 5, random = Random(it.toLong()))
            assertTrue(chromosome.size in 2..5, "subsequence length ${chromosome.size} out of range 2..5")
        }
    }

    @Test
    fun `randomSubsequence tokens all come from source command`() {
        val pool = GenePool(seeds)
        val sourceCommand = "-x1 -x2 -x3 -x4 -x5"
        val sourceTokens = sourceCommand.split(" ").filter(String::isNotBlank).toSet()
        repeat(20) {
            val chromosome = pool.randomSubsequence(listOf(sourceCommand), random = Random(it.toLong()))
            chromosome.forEach { gene ->
                assertTrue(gene.token in sourceTokens, "gene '${gene.token}' not from source command")
            }
        }
    }

    @Test
    fun `randomSubsequence with empty sources falls back to random chromosome`() {
        val pool = GenePool(seeds)
        val chromosome = pool.randomSubsequence(emptyList(), minLen = 2, maxLen = 4, random = Random(0))
        assertTrue(chromosome.size in 2..4, "fallback chromosome length ${chromosome.size} out of range 2..4")
        val vocab = pool.allGenes().map { it.token }.toSet()
        chromosome.forEach { gene ->
            assertTrue(gene.token in vocab, "fallback gene '${gene.token}' not in vocabulary")
        }
    }

    @Test
    fun `randomSubsequence with source shorter than minLen returns available tokens`() {
        val pool = GenePool(seeds)
        val shortSource = "-only"
        val chromosome = pool.randomSubsequence(listOf(shortSource), minLen = 3, maxLen = 5, random = Random(0))
        assertTrue(chromosome.isNotEmpty(), "short source should return non-empty chromosome")
    }

    @Test
    fun `randomSubsequence is contiguous subarray of source`() {
        val pool = GenePool(seeds)
        val source = "-t1 -t2 -t3 -t4 -t5 -t6 -t7 -t8"
        val tokens = source.split(" ").filter(String::isNotBlank)
        repeat(20) { seed ->
            val chromosome = pool.randomSubsequence(
                listOf(source), minLen = 2, maxLen = 4, random = Random(seed.toLong()),
            )
            val subsequence = chromosome.map { it.token }
            var found = false
            for (start in 0..tokens.size - subsequence.size) {
                if (tokens.subList(start, start + subsequence.size) == subsequence) {
                    found = true
                    break
                }
            }
            assertTrue(found, "seed=$seed: $subsequence is not a contiguous subarray of $tokens")
        }
    }

    @Test
    fun `random chromosomes keep detached option values with their owner option`() {
        val pool = GenePool(listOf("-n google.com --fake -1 --ttl 8 -Qr -a1"))
        repeat(100) { seed ->
            val tokens = pool.randomChromosome(5..12, Random(seed.toLong())).map { it.token }
            tokens.forEachIndexed { index, token ->
                if (token == "-n") assertEquals("google.com", tokens.getOrNull(index + 1))
                if (token == "--fake") assertEquals("-1", tokens.getOrNull(index + 1))
                if (token == "--ttl") assertEquals("8", tokens.getOrNull(index + 1))
            }
        }
    }
}
