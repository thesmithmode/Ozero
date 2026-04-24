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
        // DoH response: A record pointing to 1.2.3.4
        // Тело DNS-ответа собираем минимально (упрощённо: парсер умеет читать A records из RFC 1035 wire format)
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
        assertEquals("https://1.1.1.1/dns-query", DohResolver.CLOUDFLARE_ENDPOINT)
        assertEquals("https://9.9.9.9/dns-query", DohResolver.QUAD9_ENDPOINT)
    }

    @Test
    fun truncatedCompressionPointerReturnsEmpty() = runTest {
        // Обрезанный DNS ответ с compression pointer на последнем байте — не должен упасть с OOB.
        val truncated = byteArrayOf(
            0x12, 0x34, 0x81.toByte(), 0x80.toByte(),
            0, 1, 0, 1, 0, 0, 0, 0, // header: qd=1 an=1
            3, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0,
            0, 1, 0, 1, // qtype/qclass
            0xC0.toByte(), // обрезанный compression pointer: нет второго байта
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/dns-message")
                .setBody(Buffer().write(truncated)),
        )
        val result = resolver.resolve("abc")
        // Обрезано → нет A записей → Failure (пустой результат маркируется как "нет A-записей")
        assertIs<DohResult.Failure>(result)
    }

    // DNS wire format: id(2) flags(2) qd(2)=1 an(2)=1 ns(2)=0 ar(2)=0
    //                 question: qname labels, qtype=A(1), qclass=IN(1)
    //                 answer: ptr 0xc00c, type=A, class=IN, ttl=60, rdlength=4, rdata=ip
    private fun buildDnsAResponse(host: String, ipv4: IntArray): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        buf.write(byteArrayOf(0x12, 0x34)) // id
        buf.write(byteArrayOf(0x81.toByte(), 0x80.toByte())) // flags: standard response, no error
        buf.write(byteArrayOf(0, 1, 0, 1, 0, 0, 0, 0)) // qd=1 an=1 ns=0 ar=0
        // qname
        host.split(".").forEach { label ->
            buf.write(label.length)
            buf.write(label.toByteArray())
        }
        buf.write(0) // terminator
        buf.write(byteArrayOf(0, 1, 0, 1)) // qtype=A qclass=IN
        // answer: name pointer to offset 12 (start of question)
        buf.write(byteArrayOf(0xc0.toByte(), 0x0c))
        buf.write(byteArrayOf(0, 1, 0, 1)) // type=A class=IN
        buf.write(byteArrayOf(0, 0, 0, 60)) // ttl=60
        buf.write(byteArrayOf(0, 4)) // rdlength
        ipv4.forEach { buf.write(it) }
        return buf.toByteArray()
    }
}
