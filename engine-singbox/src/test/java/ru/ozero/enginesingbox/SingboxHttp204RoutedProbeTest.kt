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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HandshakeCompletedListener
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingboxHttp204RoutedProbeTest {

    @Test
    fun `dominant failure wins over last endpoint failure`() {
        val result = representativeProbeFailure(
            listOf(
                RoutedProbeFailureSample(
                    RoutedProbeResult.Reason.REMOTE_CLOSED,
                    "remote-closed",
                    "EOFException",
                    "gstatic",
                ),
                RoutedProbeFailureSample(
                    RoutedProbeResult.Reason.REMOTE_CLOSED,
                    "remote-closed",
                    "SocketException",
                    "cloudflare",
                ),
                RoutedProbeFailureSample(
                    RoutedProbeResult.Reason.UNEXPECTED_RESPONSE,
                    "unexpected-response",
                    "none",
                    "msft",
                ),
            ),
        )

        assertEquals(RoutedProbeResult.Reason.REMOTE_CLOSED, result.reason)
        assertEquals(
            "endpoints=3 dominant=remote-closed counts=remote-closed:2,unexpected-response:1 " +
                "sampleRoot=EOFException sampleEndpoint=gstatic",
            result.safeDetail,
        )
    }

    @Test
    fun `diagnostic priority breaks equal failure counts`() {
        val result = representativeProbeFailure(
            listOf(
                RoutedProbeFailureSample(
                    RoutedProbeResult.Reason.TIMEOUT,
                    "timeout",
                    "SocketTimeoutException",
                    "gstatic",
                ),
                RoutedProbeFailureSample(
                    RoutedProbeResult.Reason.TLS_CERTIFICATE,
                    "tls-certificate",
                    "CertificateException",
                    "cloudflare",
                ),
            ),
        )

        assertEquals(RoutedProbeResult.Reason.TLS_CERTIFICATE, result.reason)
    }

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
    fun `default routed probe includes every fallback endpoint`() {
        val urls = (
            listOf(SingboxHttp204RoutedProbe.PROBE_URL) +
                SingboxHttp204RoutedProbe.FALLBACK_PROBE_URLS
            )
            .distinct()

        assertTrue(urls.any { it.startsWith("https://") })
        assertTrue(urls.any { it.contains("gstatic") })
        assertTrue(urls.any { it.contains("msftconnecttest") })
        assertTrue(urls.any { it.contains("cloudflare") })
        assertEquals(3, urls.size)
    }

    @Test
    fun `HTTPS handshake failure falls back to HTTP 204 through SOCKS`() = runTest {
        SocksHttpServer(statusCode = 204, reason = "No Content").use { socks ->
            val probe = SingboxHttp204RoutedProbe(
                probeUrl = URL("https://first.example/generate_204"),
                fallbackProbeUrls = listOf(URL("http://second.example/generate_204")),
                timeoutMs = 1_000,
            )

            val latency = probe.probeLatencyMs(socks.port)

            assertTrue(latency >= 1L)
            assertTrue(socks.requestText.startsWith("GET /generate_204 "))
        }
    }

    @Test
    fun `successful HTTPS branch performs SOCKS TLS verification and GET without fallback`() = runTest {
        val handshakeCalled = AtomicBoolean(false)
        val hostnameVerified = AtomicBoolean(false)
        SocksHttpServer(statusCode = 204, reason = "No Content", acceptPlainHttps = true).use { socks ->
            val probe = SingboxHttp204RoutedProbe(
                probeUrl = URL("https://secure.example/generate_204"),
                fallbackProbeUrls = listOf(URL("http://fallback.example/generate_204")),
                timeoutMs = 1_000,
                sslSocketFactory = TestSslSocketFactory(handshakeCalled),
                hostnameVerifier = { host, _ ->
                    hostnameVerified.set(host == "secure.example")
                    true
                },
            )

            val result = probe.probe(socks.port)

            assertTrue(result is RoutedProbeResult.Success)
            assertTrue(handshakeCalled.get())
            assertTrue(hostnameVerified.get())
            assertEquals(listOf(443), socks.destinationPorts)
            assertTrue(socks.requestText.startsWith("GET /generate_204 "))
        }
    }

    @Test
    fun `routed probe fails when HTTPS and HTTP endpoints all fail`() = runTest {
        SocksHttpServer(statusCode = 204, reason = "No Content", failuresBeforeResponse = 3).use { socks ->
            val probe = SingboxHttp204RoutedProbe(
                probeUrl = URL("https://first.example/generate_204"),
                fallbackProbeUrls = listOf(
                    URL("http://second.example/generate_204"),
                    URL("http://third.example/generate_204"),
                ),
                timeoutMs = 1_000,
            )

            val latency = probe.probeLatencyMs(socks.port)

            assertEquals(SingboxHttp204RoutedProbe.LATENCY_FAILED, latency)
            assertEquals("", socks.requestText)
        }
    }

    @Test
    fun `routed probe never falls back to direct HTTP`() = runTest {
        DirectHttpServer().use { direct ->
            SocksHttpServer(statusCode = 204, reason = "No Content", failuresBeforeResponse = 1).use { socks ->
                val probe = SingboxHttp204RoutedProbe(
                    probeUrl = URL("http://127.0.0.1:${direct.port}/generate_204"),
                    fallbackProbeUrls = emptyList(),
                    timeoutMs = 1_000,
                )

                val latency = probe.probeLatencyMs(socks.port)

                assertEquals(SingboxHttp204RoutedProbe.LATENCY_FAILED, latency)
                assertFalse(direct.accepted)
            }
        }
    }

    @Test
    fun `routed probe caps configured fallback endpoints`() = runTest {
        SocksHttpServer(statusCode = 204, reason = "No Content", failuresBeforeResponse = 2).use { socks ->
            val probe = SingboxHttp204RoutedProbe(
                probeUrl = URL("http://first.example/generate_204"),
                fallbackProbeUrls = listOf(
                    URL("http://second.example/generate_204"),
                    URL("http://third.example/generate_204"),
                ),
                timeoutMs = 100,
                maxProbeUrls = 1,
            )

            val latency = probe.probeLatencyMs(socks.port)

            assertEquals(SingboxHttp204RoutedProbe.LATENCY_FAILED, latency)
            assertEquals("", socks.requestText)
        }
    }

    @Test
    fun `routed probe tries later fallback when earlier endpoints fail`() = runTest {
        SocksHttpServer(statusCode = 204, reason = "No Content", failuresBeforeResponse = 2).use { socks ->
            val probe = SingboxHttp204RoutedProbe(
                probeUrl = URL("http://first.example/generate_204"),
                fallbackProbeUrls = listOf(
                    URL("http://second.example/generate_204"),
                    URL("http://third.example/generate_204"),
                ),
                timeoutMs = 100,
            )

            val latency = probe.probeLatencyMs(socks.port)

            assertTrue(latency >= 1L)
            assertTrue(socks.requestText.startsWith("GET /generate_204 "))
        }
    }

    @Test
    fun `routed probe with zero max urls returns failed without opening connection`() = runTest {
        SocksHttpServer(statusCode = 204, reason = "No Content").use { socks ->
            val probe = SingboxHttp204RoutedProbe(
                probeUrl = URL("http://127.0.0.1/generate_204"),
                fallbackProbeUrls = emptyList(),
                timeoutMs = 1_000,
                maxProbeUrls = 0,
            )

            val latency = probe.probeLatencyMs(socks.port)

            assertEquals(SingboxHttp204RoutedProbe.LATENCY_FAILED, latency)
            assertEquals("", socks.requestText)
        }
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
    fun `routed probe rejects HTTP 200 for generate 204 endpoint`() = runTest {
        SocksHttpServer(statusCode = 200, reason = "OK").use { socks ->
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
    fun `routed probe accepts reachable Microsoft endpoint through SOCKS`() = runTest {
        SocksHttpServer(statusCode = 200, reason = "OK", body = "Microsoft Connect Test").use { socks ->
            val probe = SingboxHttp204RoutedProbe(
                probeUrl = URL("http://www.msftconnecttest.com/connecttest.txt"),
                fallbackProbeUrls = emptyList(),
                timeoutMs = 1_000,
            )

            val latency = probe.probeLatencyMs(socks.port)

            assertTrue(latency >= 1L)
            assertTrue(socks.requestText.startsWith("GET /connecttest.txt "))
        }
    }

    @Test
    fun `routed probe rejects Microsoft endpoint with rewritten body`() = runTest {
        SocksHttpServer(statusCode = 200, reason = "OK", body = "unexpected").use { socks ->
            val probe = SingboxHttp204RoutedProbe(
                probeUrl = URL("http://www.msftconnecttest.com/connecttest.txt"),
                fallbackProbeUrls = emptyList(),
                timeoutMs = 1_000,
            )

            val latency = probe.probeLatencyMs(socks.port)

            assertEquals(SingboxHttp204RoutedProbe.LATENCY_FAILED, latency)
            assertTrue(socks.requestText.startsWith("GET /connecttest.txt "))
        }
    }

    @Test
    fun `routed probe rejects HTTP redirect through SOCKS`() = runTest {
        SocksHttpServer(statusCode = 302, reason = "Found").use { socks ->
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
        failuresBeforeResponse: Int = 0,
        private val body: String = "",
        private val acceptPlainHttps: Boolean = false,
    ) : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private val failuresRemaining = AtomicInteger(failuresBeforeResponse)
        private val worker = thread(start = true, isDaemon = true) {
            while (!server.isClosed) {
                runCatching {
                    server.accept().use { socket ->
                        handle(socket)
                    }
                }
            }
        }

        @Volatile
        var requestText: String = ""

        val destinationPorts = mutableListOf<Int>()

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
            val destinationPort = input.readUnsignedShort()
            synchronized(destinationPorts) { destinationPorts += destinationPort }
            output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0))
            output.flush()
            if (destinationPort == HTTPS_PORT && !acceptPlainHttps) return
            if (failuresRemaining.getAndUpdate { (it - 1).coerceAtLeast(0) } > 0) return

            requestText = input.readHttpHeaders()
            val responseBody = body.toByteArray(StandardCharsets.US_ASCII)
            output.write(
                "HTTP/1.1 $statusCode $reason\r\nContent-Length: ${responseBody.size}\r\nConnection: close\r\n\r\n"
                    .toByteArray(StandardCharsets.US_ASCII),
            )
            output.write(responseBody)
            output.flush()
        }

        override fun close() {
            runCatching { server.close() }
            runCatching { worker.join(1_000) }
        }

        private companion object {
            private const val HTTPS_PORT = 443
        }
    }

    private class TestSslSocketFactory(
        private val handshakeCalled: AtomicBoolean,
    ) : SSLSocketFactory() {
        override fun getDefaultCipherSuites(): Array<String> = emptyArray()
        override fun getSupportedCipherSuites(): Array<String> = emptyArray()

        override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket =
            TestSslSocket(socket, handshakeCalled)

        override fun createSocket(host: String, port: Int): Socket = error("unused")
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            error("unused")
        override fun createSocket(host: InetAddress, port: Int): Socket = error("unused")
        override fun createSocket(
            address: InetAddress,
            port: Int,
            localAddress: InetAddress,
            localPort: Int,
        ): Socket = error("unused")
    }

    private class TestSslSocket(
        private val delegate: Socket,
        private val handshakeCalled: AtomicBoolean,
    ) : SSLSocket() {
        override fun startHandshake() = handshakeCalled.set(true)
        override fun getSession(): SSLSession = javax.net.ssl.SSLContext.getDefault().createSSLEngine().session
        override fun getInputStream() = delegate.getInputStream()
        override fun getOutputStream() = delegate.getOutputStream()
        override fun close() = delegate.close()
        override fun setSoTimeout(timeout: Int) {
            delegate.soTimeout = timeout
        }
        override fun getSoTimeout(): Int = delegate.soTimeout
        override fun getSupportedCipherSuites(): Array<String> = emptyArray()
        override fun getEnabledCipherSuites(): Array<String> = emptyArray()
        override fun setEnabledCipherSuites(suites: Array<out String>) = Unit
        override fun getSupportedProtocols(): Array<String> = emptyArray()
        override fun getEnabledProtocols(): Array<String> = emptyArray()
        override fun setEnabledProtocols(protocols: Array<out String>) = Unit
        override fun addHandshakeCompletedListener(listener: HandshakeCompletedListener) = Unit
        override fun removeHandshakeCompletedListener(listener: HandshakeCompletedListener) = Unit
        override fun setUseClientMode(mode: Boolean) = Unit
        override fun getUseClientMode(): Boolean = true
        override fun setNeedClientAuth(need: Boolean) = Unit
        override fun getNeedClientAuth(): Boolean = false
        override fun setWantClientAuth(want: Boolean) = Unit
        override fun getWantClientAuth(): Boolean = false
        override fun setEnableSessionCreation(flag: Boolean) = Unit
        override fun getEnableSessionCreation(): Boolean = true
    }

    private class DirectHttpServer : AutoCloseable {
        private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        private val acceptedRef = AtomicBoolean(false)
        private val worker = thread(start = true, isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    acceptedRef.set(true)
                    socket.getOutputStream().write(
                        "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            .toByteArray(StandardCharsets.US_ASCII),
                    )
                }
            }
        }

        val port: Int = server.localPort
        val accepted: Boolean
            get() = acceptedRef.get()

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
