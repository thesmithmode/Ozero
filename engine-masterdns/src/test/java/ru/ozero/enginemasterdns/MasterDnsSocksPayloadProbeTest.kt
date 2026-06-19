package ru.ozero.enginemasterdns

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ServerSocket
import kotlin.concurrent.thread

class MasterDnsSocksPayloadProbeTest {

    @Test
    fun `probe succeeds after SOCKS connect success reply`() = runTest {
        FakeSocksServer(reply = 0x00).use { server ->
            val elapsed = MasterDnsSocksPayloadProbe.probe(
                socksHost = "127.0.0.1",
                socksPort = server.port,
                timeoutMs = 1_000,
            )

            assertTrue(elapsed >= 0)
        }
    }

    @Test
    fun `probe fails after SOCKS connect failure reply`() = runTest {
        FakeSocksServer(reply = 0x05).use { server ->
            val failed = runCatching {
                MasterDnsSocksPayloadProbe.probe(
                    socksHost = "127.0.0.1",
                    socksPort = server.port,
                    timeoutMs = 1_000,
                )
            }.exceptionOrNull()

            assertTrue(failed is IOException)
        }
    }

    private class FakeSocksServer(private val reply: Int) : AutoCloseable {
        private val server = ServerSocket(0)
        val port: Int = server.localPort
        private val worker = thread(start = true) {
            runCatching {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    repeat(3) { input.read() }
                    output.write(byteArrayOf(0x05, 0x00))
                    output.flush()
                    val header = ByteArray(5)
                    input.readFully(header)
                    val hostLength = header[4].toInt() and 0xFF
                    repeat(hostLength + 2) { input.read() }
                    output.write(byteArrayOf(0x05, reply.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                    output.flush()
                }
            }
        }

        override fun close() {
            runCatching { server.close() }
            worker.join(1_000)
        }

        private fun java.io.InputStream.readFully(buffer: ByteArray) {
            var offset = 0
            while (offset < buffer.size) {
                val read = read(buffer, offset, buffer.size - offset)
                if (read < 0) throw IOException("eof")
                offset += read
            }
        }
    }
}
