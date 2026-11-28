package ru.ozero.engineurnetwork

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UrnetworkLogInitializationTest {
    @Test
    fun `rapid sessions initialize process logging only once`() {
        val initialization = UrnetworkLogInitialization()
        var calls = 0
        repeat(30) {
            initialization.runOnce { calls++ }
        }
        assertEquals(1, calls)
    }

    @Test
    fun `partial native initialization is never retried in same process`() {
        val initialization = UrnetworkLogInitialization()
        var calls = 0
        assertFailsWith<IllegalStateException> {
            initialization.runOnce {
                calls++
                error("partial initialization")
            }
        }
        initialization.runOnce { calls++ }
        assertEquals(1, calls)
    }
}
