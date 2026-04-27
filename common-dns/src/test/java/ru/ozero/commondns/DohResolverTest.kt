package ru.ozero.commondns

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DohResolverTest {
    private lateinit var server: MockWebServer
    private lateinit var resolver: DohResolver

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        resolver = DohResolver(endpoint = server.url("/dns-query").toString())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun resolvesIpv4FromDnsMessage() = runTest {
        val dnsResponse = buildDnsAResponse(host = "example.com", ipv4 = intArrayOf(1, 2, 3, 4))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/dns-message")
                .setBody(Buffer().write(dnsResponse)),
        )

        val result = resolver.resolve("example.com")
        assertIs<DohResult.Ok>(result)
        assertTrue(result.addresses.isNotEmpty())
        assertEquals("1.2.3.4", result.addresses[0])
    }

    @Test
    fun returns5xxFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(502))
        val result = resolver.resolve("example.com")
        assertIs<DohResult.Failure>(result)
        assertEquals(502, result.statusCode)
    }

    @Test
    fun returnsNetworkFailureOnReset() = runTest {
        server.enqueue(
            MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START),
        )
        val result = resolver.resolve("example.com")
        assertIs<DohResult.Failure>(result)
    }

    @Test
    fun resolverDefaultsUseCloudflare() {
        assertEquals("https://cloudflare-dns.com/dns-query", DohResolver.CLOUDFLARE_ENDPOINT)
        assertEquals("https://dns.quad9.net/dns-query", DohResolver.QUAD9_ENDPOINT)
    }

    @Test
    fun truncatedCompressionPointerReturnsEmpty() = runTest {
        val truncated = byteArrayOf(
            0x12, 0x34, 0x81.toByte(), 0x80.toByte(),
            0, 1, 0, 1, 0, 0, 0, 0,
            3, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0,
            0, 1, 0, 1,
            0xC0.toByte(),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/dns-message")
                .setBody(Buffer().write(truncated)),
        )
        val result = resolver.resolve("abc")
        assertIs<DohResult.Failure>(result)
    }

    private fun buildDnsAResponse(host: String, ipv4: IntArray): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        buf.write(byteArrayOf(0x12, 0x34))
        buf.write(byteArrayOf(0x81.toByte(), 0x80.toByte()))
        buf.write(byteArrayOf(0, 1, 0, 1, 0, 0, 0, 0))
        host.split(".").forEach { label ->
            buf.write(label.length)
            buf.write(label.toByteArray())
        }
        buf.write(0)
        buf.write(byteArrayOf(0, 1, 0, 1))
        buf.write(byteArrayOf(0xc0.toByte(), 0x0c))
        buf.write(byteArrayOf(0, 1, 0, 1))
        buf.write(byteArrayOf(0, 0, 0, 60))
        buf.write(byteArrayOf(0, 4))
        ipv4.forEach { buf.write(it) }
        return buf.toByteArray()
    }
}
