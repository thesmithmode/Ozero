package ru.ozero.enginesingbox

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SingboxHttp204RoutedProbeTest {

    @Test
    fun `routed probe rejects non positive socks ports without opening connection`() = runTest {
        val probe = SingboxHttp204RoutedProbe(
            probeUrl = URL("http://127.0.0.1/generate_204"),
            timeoutMs = 100,
        )

        assertEquals(SingboxHttp204RoutedProbe.LATENCY_FAILED, probe.probeLatencyMs(0))
        assertEquals(SingboxHttp204RoutedProbe.LATENCY_FAILED, probe.probeLatencyMs(-1))
    }

    @Test
    fun `routed probe default configuration rejects invalid port`() = runTest {
        val probe = SingboxHttp204RoutedProbe()

        assertEquals(SingboxHttp204RoutedProbe.LATENCY_FAILED, probe.probeLatencyMs(0))
    }

    @Test
    fun `routed probe succeeds after HTTP 204 through SOCKS`() = runTest {
        val ticks = 1_000L
        SocksHttpServer(statusCode = 204, reason = "No Content").use { socks ->
            val probe = SingboxHttp204RoutedProbe(
                probeUrl = URL("http://127.0.0.1/generate_204"),
                fallbackProbeUrls = emptyList(),
                timeoutMs = 1_000,
                nanoTime = { ticks },
            )

            val latency = probe.probeLatencyMs(socks.port)

            assertEquals(1L, latency)
            assertTrue(socks.requestText.startsWith("GET /generate_204 "))
        }
    }

    @Test
    fun `routed probe keeps measured latency above minimum`() = runTest {
        var ticks = 1_000L
        SocksHttpServer(statusCode = 204, reason = "No Content").use { socks ->
            val probe = SingboxHttp204RoutedProbe(
                probeUrl = URL("http://127.0.0.1/generate_204"),
                fallbackProbeUrls = emptyList(),
                timeoutMs = 1_000,
                nanoTime = {
                    val current = ticks
                    ticks += 3_000_000L
                    current
                },
            )

            val latency = probe.probeLatencyMs(socks.port)

            assertEquals(3L, latency)
            assertTrue(socks.requestText.startsWith("GET /generate_204 "))
        }
    }

    @Test
    fun `routed probe accepts HTTP 200 response through SOCKS`() = runTest {
        SocksHttpServer(statusCode = 200, reason = "OK").use { socks ->
            val probe = SingboxHttp204RoutedProbe(
                probeUrl = URL("http://127.0.0.1/generate_204"),
                fallbackProbeUrls = emptyList(),
                timeoutMs = 1_000,
            )

            val latency = probe.probeLatencyMs(socks.port)

            assertTrue(latency >= 1L)
            assertTrue(socks.requestText.startsWith("GET /generate_204 "))
        }
    }

    @Test
    fun `routed probe rejects HTTP 500 response through SOCKS`() = runTest {
        SocksHttpServer(statusCode = 500, reason = "Server Error").use { socks ->
            val probe = SingboxHttp204RoutedProbe(
                probeUrl = URL("http://127.0.0.1/generate_204"),
                fallbackProbeUrls = emptyList(),
                timeoutMs = 1_000,
            )

            val latency = probe.probeLatencyMs(socks.port)

            assertEquals(SingboxHttp204RoutedProbe.LATENCY_FAILED, latency)
            assertTrue(socks.requestText.startsWith("GET /generate_204 "))
        }
    }

    @Test
    fun `routed probe rejects TCP listener without SOCKS HTTP 204`() = runTest {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            thread(start = true, isDaemon = true) {
                runCatching { server.accept().use { } }
            }
            val probe = SingboxHttp204RoutedProbe(
                probeUrl = URL("http://127.0.0.1/generate_204"),
                fallbackProbeUrls = emptyList(),
                timeoutMs = 300,
            )

            val latency = probe.probeLatencyMs(server.localPort)

            assertEquals(SingboxHttp204RoutedProbe.LATENCY_FAILED, latency)
        }
    }

    @Test
    fun `routed probe returns failed when socks port is closed`() = runTest {
        val closedPort = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        val probe = SingboxHttp204RoutedProbe(
            probeUrl = URL("http://127.0.0.1/generate_204"),
            fallbackProbeUrls = emptyList(),
            timeoutMs = 100,
        )

        val latency = probe.probeLatencyMs(closedPort)

        assertEquals(SingboxHttp204RoutedProbe.LATENCY_FAILED, latency)
    }

    @Test
    fun `routed probe maps non http connection failures to failed latency`() = runTest {
        val probe = SingboxHttp204RoutedProbe(
            probeUrl = URL("file:probe-resource"),
            fallbackProbeUrls = emptyList(),
            timeoutMs = 100,
        )

        val latency = probe.probeLatencyMs(1)

        assertEquals(SingboxHttp204RoutedProbe.LATENCY_FAILED, latency)
    }

    private class SocksHttpServer(
        private val statusCode: Int,
        private val reason: String,
    ) : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private val worker = thread(start = true, isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    handle(socket)
                }
            }
        }

        @Volatile
        var requestText: String = ""

        val port: Int = server.localPort

        private fun handle(socket: Socket) {
            val input = DataInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            assertEquals(5, input.readUnsignedByte())
            val methods = input.readUnsignedByte()
            repeat(methods) { input.readUnsignedByte() }
            output.write(byteArrayOf(5, 0))
            output.flush()

            assertEquals(5, input.readUnsignedByte())
            assertEquals(1, input.readUnsignedByte())
            input.readUnsignedByte()
            when (input.readUnsignedByte()) {
                1 -> input.skipNBytesCompat(4)
                3 -> input.skipNBytesCompat(input.readUnsignedByte().toLong())
                4 -> input.skipNBytesCompat(16)
                else -> error("unsupported address type")
            }
            input.skipNBytesCompat(2)
            output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0))
            output.flush()

            requestText = input.readHttpHeaders()
            output.write(
                "HTTP/1.1 $statusCode $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    .toByteArray(StandardCharsets.US_ASCII),
            )
            output.flush()
        }

        override fun close() {
            runCatching { server.close() }
            runCatching { worker.join(1_000) }
        }
    }
}

private fun DataInputStream.skipNBytesCompat(count: Long) {
    var remaining = count
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped > 0) {
            remaining -= skipped
        } else {
            readUnsignedByte()
            remaining--
        }
    }
}

private fun DataInputStream.readHttpHeaders(): String {
    val bytes = ByteArrayOutputStream()
    var matched = 0
    while (matched < 4) {
        val next = read()
        if (next < 0) break
        bytes.write(next)
        matched = when {
            matched == 0 && next == '\r'.code -> 1
            matched == 1 && next == '\n'.code -> 2
            matched == 2 && next == '\r'.code -> 3
            matched == 3 && next == '\n'.code -> 4
            next == '\r'.code -> 1
            else -> 0
        }
    }
    return bytes.toString(StandardCharsets.US_ASCII.name())
}
