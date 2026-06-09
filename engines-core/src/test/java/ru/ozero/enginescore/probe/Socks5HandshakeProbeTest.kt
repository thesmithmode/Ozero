package ru.ozero.enginescore.probe

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Socks5HandshakeProbeTest {

    @Test
    fun `probe completes when server accepts no auth method`() = runTest {
        val server = OneShotSocksServer(byteArrayOf(0x05, 0x00))

        val latency = Socks5HandshakeProbe.probe("127.0.0.1", server.port, timeoutMs = 1_000)

        assertTrue(latency >= 0)
        assertTrue(server.received.contentEquals(byteArrayOf(0x05, 0x01, 0x00)))
    }

    @Test
    fun `probe uses default timeout when caller omits it`() = runTest {
        val server = OneShotSocksServer(byteArrayOf(0x05, 0x00))

        val latency = Socks5HandshakeProbe.probe("127.0.0.1", server.port)

        assertTrue(latency >= 0)
        assertTrue(server.received.contentEquals(byteArrayOf(0x05, 0x01, 0x00)))
    }

    @Test
    fun `probe rejects invalid port and timeout`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            Socks5HandshakeProbe.probe("127.0.0.1", 0, timeoutMs = 1_000)
        }
        assertFailsWith<IllegalArgumentException> {
            Socks5HandshakeProbe.probe("127.0.0.1", 65_536, timeoutMs = 1_000)
        }
        assertFailsWith<IllegalArgumentException> {
            Socks5HandshakeProbe.probe("127.0.0.1", 1080, timeoutMs = 0)
        }
    }

    @Test
    fun `probe propagates connection failure`() = runTest {
        val closedPort = ServerSocket(0).use { it.localPort }

        assertFailsWith<IOException> {
            Socks5HandshakeProbe.probe("127.0.0.1", closedPort, timeoutMs = 200)
        }
    }

    @Test
    fun `probe rejects bad socks version`() = runTest {
        val server = OneShotSocksServer(byteArrayOf(0x04, 0x00))

        val error = assertFailsWith<IOException> {
            Socks5HandshakeProbe.probe("127.0.0.1", server.port, timeoutMs = 1_000)
        }

        assertTrue(error.message!!.contains("bad SOCKS version"))
    }

    @Test
    fun `probe rejects all auth methods response`() = runTest {
        val server = OneShotSocksServer(byteArrayOf(0x05, 0xFF.toByte()))

        val error = assertFailsWith<IOException> {
            Socks5HandshakeProbe.probe("127.0.0.1", server.port, timeoutMs = 1_000)
        }

        assertTrue(error.message!!.contains("rejected"))
    }

    @Test
    fun `probe rejects unsupported auth method`() = runTest {
        val server = OneShotSocksServer(byteArrayOf(0x05, 0x02))

        val error = assertFailsWith<IOException> {
            Socks5HandshakeProbe.probe("127.0.0.1", server.port, timeoutMs = 1_000)
        }

        assertTrue(error.message!!.contains("unsupported method=2"))
    }

    private class OneShotSocksServer(private val response: ByteArray) {
        private val socket = ServerSocket(0)
        val port: Int = socket.localPort

        @Volatile
        var received: ByteArray = ByteArray(0)

        init {
            thread(isDaemon = true) {
                socket.use { server ->
                    server.accept().use { client ->
                        val input = client.getInputStream()
                        received = ByteArray(3)
                        var offset = 0
                        while (offset < received.size) {
                            val read = input.read(received, offset, received.size - offset)
                            if (read < 0) break
                            offset += read
                        }
                        client.getOutputStream().write(response)
                        client.getOutputStream().flush()
                    }
                }
            }
        }
    }
}
