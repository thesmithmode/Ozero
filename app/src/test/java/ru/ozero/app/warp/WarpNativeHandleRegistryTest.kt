package ru.ozero.app.warp

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class WarpNativeHandleRegistryTest {
    @Test
    fun `register accepts zero as a valid native handle`() {
        val released = mutableListOf<Int>()
        val registry = WarpNativeHandleRegistry(released::add)

        registry.register(0)
        registry.releaseAll()

        assertEquals(listOf(0), released)
    }

    @Test
    fun `register ignores failed native handles`() {
        val released = mutableListOf<Int>()
        val registry = WarpNativeHandleRegistry(released::add)

        registry.register(-1)
        registry.releaseAll()

        assertEquals(emptyList(), released)
    }

    @Test
    fun `release invokes native cleanup once`() {
        val released = mutableListOf<Int>()
        val registry = WarpNativeHandleRegistry(released::add)

        registry.register(4)
        registry.release(4)
        registry.release(4)

        assertEquals(listOf(4), released)
    }

    @Test
    fun `release all drains every active handle exactly once`() {
        val released = mutableListOf<Int>()
        val registry = WarpNativeHandleRegistry(released::add)

        registry.register(0)
        registry.register(7)
        registry.releaseAll()
        registry.releaseAll()

        assertEquals(setOf(0, 7), released.toSet())
        assertEquals(2, released.size)
    }

    @Test
    fun `single release excludes handle from later emergency cleanup`() {
        val released = mutableListOf<Int>()
        val registry = WarpNativeHandleRegistry(released::add)

        registry.register(3)
        registry.register(8)
        registry.release(3)
        registry.releaseAll()

        assertEquals(setOf(3, 8), released.toSet())
        assertEquals(2, released.size)
    }

    @Test
    fun `failed emergency cleanup keeps handle for retry`() {
        val released = mutableListOf<Int>()
        var shouldFail = true
        val registry = WarpNativeHandleRegistry { handle ->
            if (shouldFail) error("native stop failed")
            released += handle
        }
        registry.register(0)

        assertFalse(registry.releaseAll())
        shouldFail = false
        assertTrue(registry.releaseAll())

        assertEquals(listOf(0), released)
    }
}
