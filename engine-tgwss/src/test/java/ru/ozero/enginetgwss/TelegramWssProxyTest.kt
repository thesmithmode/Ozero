package ru.ozero.enginetgwss

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class TelegramWssProxyTest {
    @Test
    fun `loopback listener rejects non Telegram destinations without network access`() {
        TelegramWssProxy().use { proxy ->
            val port = proxy.start()
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 2000
                val output = socket.getOutputStream()
                val input = DataInputStream(socket.getInputStream())
                output.write(byteArrayOf(5, 1, 0, 5, 1, 0, 1, 127, 0, 0, 1, 1, 187.toByte()))
                output.flush()
                val response = ByteArray(12).also { input.readFully(it) }
                assertArrayEquals(byteArrayOf(5, 0, 5, 2, 0, 1, 0, 0, 0, 0, 0, 0), response)
                assertEquals(-1, input.read())
            }
            assertEquals(0L, proxy.bytesIn)
            assertEquals(0L, proxy.bytesOut)
        }
    }

    @Test
    fun `close releases listener and prevents restart`() {
        val proxy = TelegramWssProxy()
        val port = proxy.start()
        assertTrue(port > 0)
        proxy.close()
        proxy.close()
        assertThrows(IllegalStateException::class.java) { proxy.start() }
        assertThrows(IOException::class.java) {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1000) }
        }
    }
}
