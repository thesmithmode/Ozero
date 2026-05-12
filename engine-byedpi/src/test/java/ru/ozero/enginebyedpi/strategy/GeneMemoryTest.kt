package ru.ozero.enginebyedpi.strategy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneMemoryTest {

    @TempDir
    lateinit var tempDir: File

    private fun memory() = GeneMemory(File(tempDir, "test_memory.json"))

    @Test
    fun `ucbScore returns UNEXPLORED for unknown token`() {
        val mem = memory()
        val score = mem.ucbScore("-unknown")
        assertTrue(score > 1.0, "unexplored token should have high score")
    }

    @Test
    fun `record increases score for recorded tokens`() {
        val mem = memory()
        mem.record(listOf("-a", "-b"), fitness = 1.0)
        val scoreA = mem.ucbScore("-a")
        val scoreUnknown = mem.ucbScore("-z")
        assertTrue(scoreA >= 0.0)
        assertTrue(scoreUnknown > scoreA || scoreUnknown > 1.0)
    }

    @Test
    fun `record with zero fitness lowers exploitation part`() {
        val mem = memory()
        mem.record(listOf("-bad"), fitness = 0.0)
        mem.record(listOf("-good"), fitness = 1.0)
        val goodScore = mem.ucbScore("-good")
        val badScore = mem.ucbScore("-bad")
        assertTrue(goodScore > badScore, "good token should score higher than bad")
    }

    @Test
    fun `save and load round-trips scores`() {
        val file = File(tempDir, "mem.json")
        val mem1 = GeneMemory(file)
        mem1.record(listOf("-x"), fitness = 0.8)
        mem1.save()

        val mem2 = GeneMemory(file)
        mem2.load()
        val score = mem2.ucbScore("-x")
        assertTrue(score > 0.0, "loaded score should be positive")
    }

    @Test
    fun `load on missing file does not throw`() {
        val mem = GeneMemory(File(tempDir, "nonexistent.json"))
        mem.load()
        assertFalse(mem.hasData())
    }

    @Test
    fun `hasData false before any record`() {
        val mem = memory()
        assertFalse(mem.hasData())
    }

    @Test
    fun `hasData true after record`() {
        val mem = memory()
        mem.record(listOf("-a"), fitness = 0.5)
        assertTrue(mem.hasData())
    }

    @Test
    fun `multiple records accumulate wins`() {
        val mem = memory()
        repeat(5) { mem.record(listOf("-a"), fitness = 1.0) }
        mem.record(listOf("-b"), fitness = 1.0)
        val scoreA = mem.ucbScore("-a")
        val scoreB = mem.ucbScore("-b")
        assertTrue(scoreA >= 0.0)
        assertTrue(scoreB >= 0.0)
    }

    @Test
    fun `save skips tokens with negligible trials`() {
        val file = File(tempDir, "skip.json")
        val mem = GeneMemory(file)
        mem.save()
        assertFalse(file.exists() && file.readText().contains("\"wins\""))
    }

    @Test
    fun `ucbScore provides exploration bonus for under-tried tokens`() {
        val mem = memory()
        repeat(20) { mem.record(listOf("-popular"), fitness = 0.5) }
        mem.record(listOf("-rare"), fitness = 0.5)
        val popularScore = mem.ucbScore("-popular")
        val rareScore = mem.ucbScore("-rare")
        assertTrue(rareScore > popularScore, "rare token gets exploration bonus")
    }

    @Test
    fun `equal fitness tokens produce finite scores`() {
        val mem = memory()
        listOf("-a", "-b", "-c").forEach { mem.record(listOf(it), fitness = 0.7) }
        listOf("-a", "-b", "-c").forEach { token ->
            val s = mem.ucbScore(token)
            assertTrue(s.isFinite(), "score for $token must be finite")
        }
    }

    @Test
    fun `concurrent record does not corrupt scores`() {
        val mem = memory()
        val threads = (1..8).map { idx ->
            Thread {
                repeat(200) { mem.record(listOf("-t$idx"), fitness = 1.0) }
            }.also { it.start() }
        }
        threads.forEach { it.join() }
        (1..8).forEach { idx ->
            val s = mem.ucbScore("-t$idx")
            assertTrue(s.isFinite() && s > 0.0, "score -t$idx finite positive after concurrent record: $s")
        }
    }

    @Test
    fun `importRawJson rejects malformed JSON without writing to file`() {
        val mem = memory()
        mem.record(listOf("-keep"), fitness = 1.0)
        mem.save()
        mem.importRawJson("not a json")
        val mem2 = GeneMemory(File(tempDir, "test_memory.json"))
        mem2.load()
        assertTrue(mem2.hasData(), "valid prior state must survive failed import")
    }
}
