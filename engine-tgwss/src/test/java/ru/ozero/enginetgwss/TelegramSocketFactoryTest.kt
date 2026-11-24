package ru.ozero.enginetgwss

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.net.Socket

class TelegramSocketFactoryTest {
    @Test
    fun `protection happens on the unconnected socket`() {
        var protected: Socket? = null
        val factory = TelegramSocketFactory {
            assertFalse(it.isConnected)
            protected = it
            true
        }
        factory.createSocket().use { assertSame(protected, it) }
    }

    @Test
    fun `failed protection closes socket and denies connection`() {
        var protected: Socket? = null
        val factory = TelegramSocketFactory {
            protected = it
            false
        }
        assertThrows(IOException::class.java) { factory.createSocket() }
        assertTrue(protected!!.isClosed)
    }

    @Test
    fun `throwing protection closes socket`() {
        var protected: Socket? = null
        val factory = TelegramSocketFactory {
            protected = it
            throw IllegalStateException()
        }
        assertThrows(IllegalStateException::class.java) { factory.createSocket() }
        assertTrue(protected!!.isClosed)
    }

    @Test
    fun `connected socket overloads cannot bypass protection`() {
        val factory = TelegramSocketFactory { error("Unexpected call") }
        val ip = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        assertThrows(IOException::class.java) { factory.createSocket("localhost", 443) }
        assertThrows(IOException::class.java) { factory.createSocket("localhost", 443, ip, 0) }
        assertThrows(IOException::class.java) { factory.createSocket(ip, 443) }
        assertThrows(IOException::class.java) { factory.createSocket(ip, 443, ip, 0) }
    }
}
