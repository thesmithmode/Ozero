package ru.ozero.enginegfvpn

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GfVpnExitSocketFactoryTest {
    @Test
    fun `exit lookup routes through authenticated SDK without resolving target locally`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                val peer = executor.submit {
                    server.accept().use { socket ->
                        socket.soTimeout = 2_000
                        val input = DataInputStream(socket.getInputStream())
                        val output = socket.getOutputStream()
                        fun read(size: Int) = ByteArray(size).also(input::readFully)
                        assertArrayEquals(byteArrayOf(5, 1, 2), read(3))
                        output.write(byteArrayOf(5, 2))
                        assertEquals(1, input.readUnsignedByte())
                        assertEquals("user", read(input.readUnsignedByte()).toString(Charsets.UTF_8))
                        assertEquals("secret", read(input.readUnsignedByte()).toString(Charsets.UTF_8))
                        output.write(byteArrayOf(1, 0))
                        assertArrayEquals(byteArrayOf(5, 1, 0, 3), read(4))
                        assertEquals("ifconfig.co", read(input.readUnsignedByte()).toString(Charsets.US_ASCII))
                        assertEquals(443, input.readUnsignedShort())
                        output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 1))
                    }
                }
                val address = InetAddress.getByAddress("ifconfig.co", byteArrayOf(127, 0, 0, 1))
                GfVpnExitSocketFactory(server.localPort, "user", "secret").createSocket().use {
                    it.connect(InetSocketAddress(address, 443), 2_000)
                }
                peer.get(3, TimeUnit.SECONDS)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `unexpected destination fails before connecting`() {
        GfVpnExitSocketFactory(1, "user", "secret").createSocket().use { socket ->
            assertThrows(IOException::class.java) {
                socket.connect(InetSocketAddress.createUnresolved("example.com", 443), 100)
            }
        }
    }
}
