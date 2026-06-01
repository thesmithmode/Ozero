package ru.ozero.enginebyedpi.strategy

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SocksProbeClientTest {

    private fun fakeConnection(
        responseCode: Int,
        contentLength: Long,
        body: ByteArray,
    ): HttpURLConnection {
        val conn = mockk<HttpURLConnection>(relaxed = true)
        every { conn.responseCode } returns responseCode
        every { conn.contentLengthLong } returns contentLength
        every { conn.inputStream } returns ByteArrayInputStream(body)
        every { conn.errorStream } returns ByteArrayInputStream(body)
        return conn
    }

    private fun client(
        opener: (URL, Proxy) -> HttpURLConnection,
        clock: () -> Long = { 0L },
        maxBytesToRead: Long = HttpSocksProbeClient.DEFAULT_MAX_BYTES,
    ) = HttpSocksProbeClient(
        proxyHost = "127.0.0.1",
        proxyPort = 1080,
        timeoutMs = 5000,
        maxBytesToRead = maxBytesToRead,
        urlOpener = opener,
        nowMs = clock,
    )

    @Test
    fun `success когда actual not less than declared length`() = runTest {
        val body = ByteArray(100) { it.toByte() }
        val opener = { _: URL, _: Proxy -> fakeConnection(200, 100L, body) }
        val result = client(opener).probe("example.com")
        assertTrue(result.success, "actual=$body.size >= declared=100 → success")
        assertEquals(200, result.responseCode)
        assertEquals(100L, result.declaredLength)
        assertEquals(100L, result.actualLength)
    }

    @Test
    fun `success когда declared zero or less (chunked encoding или unknown)`() = runTest {
        val body = ByteArray(50)
        val opener = { _: URL, _: Proxy -> fakeConnection(200, -1L, body) }
        val result = client(opener).probe("example.com")
        assertTrue(result.success, "declared <= 0 → разрешаем (нет ground truth)")
    }

    @Test
    fun `block когда actual меньше declared (ТСПУ truncate)`() = runTest {
        val body = ByteArray(50)
        val opener = { _: URL, _: Proxy -> fakeConnection(200, 200L, body) }
        val result = client(opener).probe("blocked.com")
        assertFalse(result.success, "actual=50 < declared=200 → block")
        assertEquals(50L, result.actualLength)
        assertEquals(200L, result.declaredLength)
    }

    @Test
    fun `exception в openConnection — failure с error class without message`() = runTest {
        val opener = { _: URL, _: Proxy -> throw IOException("connection refused to example.com:443") }
        val result = client(opener).probe("example.com")
        assertFalse(result.success)
        assertNotNull(result.error)
        assertEquals("IOException", result.error)
        assertFalse(result.error!!.contains("example.com"), "hostname must not leak via error message")
    }

    @Test
    fun `invalid url — failure`() = runTest {
        val opener = { _: URL, _: Proxy -> fakeConnection(200, 0L, ByteArray(0)) }
        val result = client(opener).probe(":not_a_url:")
        assertFalse(result.success)
        assertNotNull(result.error)
    }

    @Test
    fun `formatUrl добавляет https если нет схемы`() = runTest {
        var capturedUrl: URL? = null
        val opener: (URL, Proxy) -> HttpURLConnection = { url, _ ->
            capturedUrl = url
            fakeConnection(200, 0L, ByteArray(0))
        }
        client(opener).probe("youtube.com")
        assertEquals("https://youtube.com", capturedUrl?.toString())
    }

    @Test
    fun `formatUrl сохраняет http схему`() = runTest {
        var capturedUrl: URL? = null
        val opener: (URL, Proxy) -> HttpURLConnection = { url, _ ->
            capturedUrl = url
            fakeConnection(200, 0L, ByteArray(0))
        }
        client(opener).probe("http://example.com")
        assertEquals("http://example.com", capturedUrl?.toString())
    }

    @Test
    fun `durationMs считается через nowMs`() = runTest {
        var clock = 1000L
        val opener: (URL, Proxy) -> HttpURLConnection = { _, _ ->
            clock += 250L
            fakeConnection(200, 10L, ByteArray(10))
        }
        val result = client(opener, clock = { clock }).probe("example.com")
        assertTrue(result.durationMs >= 250L, "duration=${result.durationMs} >= 250")
    }

    @Test
    fun `proxy SOCKS5 на 127_0_0_1 portN`() = runTest {
        var capturedProxy: Proxy? = null
        val opener: (URL, Proxy) -> HttpURLConnection = { _, proxy ->
            capturedProxy = proxy
            fakeConnection(200, 0L, ByteArray(0))
        }
        HttpSocksProbeClient(
            proxyHost = "127.0.0.1",
            proxyPort = 9999,
            urlOpener = opener,
        ).probe("example.com")
        assertEquals(Proxy.Type.SOCKS, capturedProxy?.type())
        assertNotNull(capturedProxy)
    }

    @Test
    fun `formatUrl preserves https scheme`() = runTest {
        var capturedUrl: URL? = null
        val opener: (URL, Proxy) -> HttpURLConnection = { url, _ ->
            capturedUrl = url
            fakeConnection(200, 0L, ByteArray(0))
        }
        client(opener).probe("https://example.com")
        assertEquals("https://example.com", capturedUrl?.toString())
    }

    @Test
    fun `non 2xx response reads error stream`() = runTest {
        val conn = mockk<HttpURLConnection>(relaxed = true)
        every { conn.responseCode } returns 403
        every { conn.contentLengthLong } returns 4L
        every { conn.errorStream } returns ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
        every { conn.inputStream } throws AssertionError("input stream must not be used")

        val result = client { _, _ -> conn }.probe("blocked.com")

        assertTrue(result.success)
        assertEquals(403, result.responseCode)
        assertEquals(4L, result.actualLength)
    }

    @Test
    fun `null error stream returns zero length and failure for declared body`() = runTest {
        val conn = mockk<HttpURLConnection>(relaxed = true)
        every { conn.responseCode } returns 503
        every { conn.contentLengthLong } returns 10L
        every { conn.errorStream } returns null

        val result = client { _, _ -> conn }.probe("blocked.com")

        assertFalse(result.success)
        assertEquals(0L, result.actualLength)
        assertEquals(10L, result.declaredLength)
    }

    @Test
    fun `unknown declared length stops at configured max bytes`() = runTest {
        val body = ByteArray(10) { it.toByte() }
        val opener = { _: URL, _: Proxy -> fakeConnection(200, -1L, body) }

        val result = client(opener, maxBytesToRead = 4L).probe("example.com")

        assertTrue(result.success)
        assertEquals(4L, result.actualLength)
    }

    @Test
    fun `read IOException returns bytes read before failure`() = runTest {
        val stream = object : InputStream() {
            var calls = 0

            override fun read(): Int = error("single byte read is not used")

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                calls++
                if (calls > 1) throw IOException("truncated")
                buffer[offset] = 1
                buffer[offset + 1] = 2
                return 2
            }
        }
        val conn = mockk<HttpURLConnection>(relaxed = true)
        every { conn.responseCode } returns 200
        every { conn.contentLengthLong } returns 4L
        every { conn.inputStream } returns stream

        val result = client { _, _ -> conn }.probe("example.com")

        assertFalse(result.success)
        assertEquals(2L, result.actualLength)
    }

    @Test
    fun `default constants`() {
        assertEquals(5_000, HttpSocksProbeClient.DEFAULT_TIMEOUT_MS)
        assertEquals(1L * 1024 * 1024, HttpSocksProbeClient.DEFAULT_MAX_BYTES)
    }
}
