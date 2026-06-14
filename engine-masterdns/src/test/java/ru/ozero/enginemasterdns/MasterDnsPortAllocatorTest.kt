package ru.ozero.enginemasterdns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket

class MasterDnsPortAllocatorTest {

    @Test
    fun `returns desired port when free inside range`() {
        val allocator = MasterDnsPortAllocator()
        val port = allocator.allocate(18555)
        assertTrue(port in 18000..18999) { "got=$port" }
    }

    @Test
    fun `falls back to other range port when desired busy`() {
        ServerSocket(0).use { server ->
            val busy = server.localPort
            val port = MasterDnsPortAllocator().allocate(busy)
            assertNotEquals(busy, port)
            assertTrue(port in 18000..18999)
        }
    }

    @Test
    fun `falls back within range when desired below range`() {
        val port = MasterDnsPortAllocator().allocate(0)
        assertTrue(port in 18000..18999)
    }

    @Test
    fun `falls back within range when desired above range`() {
        val port = MasterDnsPortAllocator().allocate(60_000)
        assertTrue(port in 18000..18999)
    }

    @Test
    fun `narrow range with all busy throws`() {
        ServerSocket(0).use { busy ->
            val narrow = busy.localPort..busy.localPort
            assertThrows(IllegalStateException::class.java) {
                MasterDnsPortAllocator(narrow).allocate(busy.localPort)
            }
        }
    }

    @Test
    fun `custom range respected`() {
        val allocator = MasterDnsPortAllocator(19000..19010)
        val port = allocator.allocate(19005)
        assertEquals(19005, port)
    }

    @Test
    fun `busy desired inside custom range falls back to next free port`() {
        ServerSocket(0).use { busy ->
            val range = busy.localPort..(busy.localPort + 1)

            val port = MasterDnsPortAllocator(range).allocate(busy.localPort)

            assertEquals(busy.localPort + 1, port)
        }
    }
}
