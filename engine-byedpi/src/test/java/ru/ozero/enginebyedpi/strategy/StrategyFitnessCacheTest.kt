package ru.ozero.enginebyedpi.strategy

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StrategyFitnessCacheTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var file: File
    private var clock: Long = 1_000L

    @BeforeEach
    fun setUp() {
        file = File(tempDir, "fc.json")
        clock = 1_000L
    }

    private fun newCache(ttlMs: Long = 10_000L): StrategyFitnessCache =
        StrategyFitnessCache(file = file, ttlMs = ttlMs, nowMs = { clock })

    @Test
    fun `get returns null when nothing stored`() {
        val cache = newCache()
        assertNull(cache.get("-K"))
    }

    @Test
    fun `put then get returns stored fitness`() {
        val cache = newCache()
        cache.put("-K -s2", 0.42)
        assertEquals(0.42, cache.get("-K -s2"))
    }

    @Test
    fun `get returns null when entry past TTL`() {
        val cache = newCache(ttlMs = 5_000L)
        cache.put("-K", 0.7)
        clock += 6_000L
        assertNull(cache.get("-K"))
    }

    @Test
    fun `get returns value while within TTL`() {
        val cache = newCache(ttlMs = 5_000L)
        cache.put("-K", 0.9)
        clock += 4_000L
        assertEquals(0.9, cache.get("-K"))
    }

    @Test
    fun `save then load round-trips entries`() {
        val a = newCache()
        a.put("-cmd1", 0.5)
        a.put("-cmd2", 0.8)
        a.save()
        val b = newCache()
        b.load()
        assertEquals(0.5, b.get("-cmd1"))
        assertEquals(0.8, b.get("-cmd2"))
    }

    @Test
    fun `load from missing file is a no-op`() {
        val cache = newCache()
        cache.load()
        assertEquals(0, cache.size())
    }

    @Test
    fun `clearStale removes expired entries only`() {
        val cache = newCache(ttlMs = 5_000L)
        cache.put("-fresh", 0.6)
        clock += 6_000L
        cache.put("-new", 0.7)
        cache.clearStale()
        assertNull(cache.get("-fresh"))
        assertEquals(0.7, cache.get("-new"))
    }

    @Test
    fun `clear empties cache and deletes file`() {
        val cache = newCache()
        cache.put("-x", 1.0)
        cache.save()
        cache.clear()
        assertEquals(0, cache.size())
        assertEquals(false, file.exists())
    }

    @Test
    fun `clear without file still empties cache`() {
        val cache = newCache()
        cache.put("-x", 1.0)
        cache.clear()
        assertEquals(0, cache.size())
        assertEquals(false, file.exists())
    }

    @Test
    fun `put overwrites previous value for same command`() {
        val cache = newCache()
        cache.put("-K", 0.1)
        cache.put("-K", 0.99)
        assertEquals(0.99, cache.get("-K"))
    }

    @Test
    fun `size reflects number of unique commands`() {
        val cache = newCache()
        cache.put("-a", 0.1)
        cache.put("-b", 0.2)
        cache.put("-c", 0.3)
        assertEquals(3, cache.size())
    }

    @Test
    fun `corrupted file does not throw on load`() {
        file.writeText("not json {{{")
        val cache = newCache()
        cache.load()
        assertEquals(0, cache.size())
    }

    @Test
    fun `expired entry removed on get is gone from size`() {
        val cache = newCache(ttlMs = 1_000L)
        cache.put("-K", 0.5)
        clock += 2_000L
        cache.get("-K")
        assertEquals(0, cache.size())
    }

    @Test
    fun `get returns null for zero fitness to purge poisoned cache entries`() {
        val cache = newCache()
        cache.put("-K -s2 -e2 -An -f0x81", 0.0)
        assertNull(cache.get("-K -s2 -e2 -An -f0x81"), "zero fitness must be treated as stale/poisoned")
    }

    @Test
    fun `get returns null for negative fitness`() {
        val cache = newCache()
        cache.put("-cmd", -0.1)
        assertNull(cache.get("-cmd"))
    }
}
