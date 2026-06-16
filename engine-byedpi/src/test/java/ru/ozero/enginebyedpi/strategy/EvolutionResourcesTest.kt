package ru.ozero.enginebyedpi.strategy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EvolutionResourcesTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `forNetwork returns distinct resources per network id`() {
        val provider = DefaultEvolutionResourcesProvider(tempDir)
        val a = provider.forNetwork("net-a")
        val b = provider.forNetwork("net-b")
        assertNotSame(a.memory, b.memory)
        assertNotSame(a.fitnessCache, b.fitnessCache)
    }

    @Test
    fun `forNetwork returns same instance for repeated id`() {
        val provider = DefaultEvolutionResourcesProvider(tempDir)
        val a1 = provider.forNetwork("same")
        val a2 = provider.forNetwork("same")
        assertSame(a1.memory, a2.memory)
        assertSame(a1.fitnessCache, a2.fitnessCache)
    }

    @Test
    fun `file paths differ by network id`() {
        val provider = DefaultEvolutionResourcesProvider(tempDir)
        provider.forNetwork("alpha").fitnessCache.put("-K", 0.5)
        provider.forNetwork("alpha").fitnessCache.save()
        provider.forNetwork("beta").fitnessCache.put("-K", 0.9)
        provider.forNetwork("beta").fitnessCache.save()
        val alphaFile = File(tempDir, "fitness_cache_alpha.json")
        val betaFile = File(tempDir, "fitness_cache_beta.json")
        assertTrue(alphaFile.exists())
        assertTrue(betaFile.exists())
    }

    @Test
    fun `blank network id replaced with unknown`() {
        val provider = DefaultEvolutionResourcesProvider(tempDir)
        val res = provider.forNetwork("")
        assertEquals("unknown", res.networkId)
    }

    @Test
    fun `unsafe characters in network id are stripped`() {
        val provider = DefaultEvolutionResourcesProvider(tempDir)
        val res = provider.forNetwork("../../etc/passwd")
        assertTrue(res.networkId.none { it == '.' || it == '/' || it == '\\' })
    }

    @Test
    fun `safe separators are preserved in network id`() {
        val provider = DefaultEvolutionResourcesProvider(tempDir)
        val res = provider.forNetwork("wifi_5g-home")
        assertEquals("wifi_5g-home", res.networkId)
    }

    @Test
    fun `network id length capped`() {
        val provider = DefaultEvolutionResourcesProvider(tempDir)
        val res = provider.forNetwork("a".repeat(500))
        assertTrue(res.networkId.length <= 32)
    }

    @Test
    fun `caches survive provider lifecycle via on-disk load`() {
        val provider1 = DefaultEvolutionResourcesProvider(tempDir)
        provider1.forNetwork("persist").fitnessCache.apply {
            put("-K", 0.77)
            save()
        }
        val provider2 = DefaultEvolutionResourcesProvider(tempDir)
        assertEquals(0.77, provider2.forNetwork("persist").fitnessCache.get("-K"))
    }
}
