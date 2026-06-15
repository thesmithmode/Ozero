package ru.ozero.enginewarp

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.io.ByteArrayInputStream
import java.io.IOException

class HttpUrlConnectionClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: HttpUrlConnectionClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = HttpUrlConnectionClient(connectTimeoutMs = 5_000, readTimeoutMs = 5_000)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `postJson success on HTTP 200`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val url = server.url("/api/warp").toString()
        val result = client.postJson(url, "{}", "test-agent")
        assertTrue(result.isSuccess)
        assertEquals("""{"ok":true}""", result.getOrThrow())
    }

    @Test
    fun `postJson sets expected headers`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val url = server.url("/api/warp").toString()
        client.postJson(url, """{"test":1}""", "ua")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("application/json", request.getHeader("Content-Type"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("ua", request.getHeader("User-Agent"))
        assertEquals("""{"test":1}""", request.body.readUtf8())
    }

    @Test
    fun `postJson failure on HTTP 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        val url = server.url("/api/warp").toString()
        val result = client.postJson(url, "{}", "ua")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun `postJson maps non-ok response without body as failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val url = server.url("/api/warp").toString()
        val result = client.postJson(url, "{}", "ua")
        assertTrue(result.isFailure)
        assertEquals("HTTP 500: ", result.exceptionOrNull()?.message.orEmpty())
    }

    @Test
    fun `postJson failure on HTTP 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        val url = server.url("/api/warp").toString()
        val result = client.postJson(url, "{}", "ua")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("404") == true)
    }

    @Test
    fun `postJson failure on unreachable host`() = runTest {
        val result = client.postJson("http://127.0.0.1:1", "{}", "ua")
        assertTrue(result.isFailure)
    }

    @Test
    fun `postJson failure on malformed url`() = runTest {
        val result = client.postJson(":", "{}", "ua")
        assertTrue(result.isFailure)
    }

    @Test
    fun `postJson success with empty body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val url = server.url("/api/warp").toString()
        val result = client.postJson(url, "{}", "ua")
        assertTrue(result.isSuccess)
        assertEquals("", result.getOrThrow())
    }

    @Test
    fun `postJson accepts 201 and rejects 300`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"created":true}"""))
        server.enqueue(MockResponse().setResponseCode(300).setBody("redirect"))
        val url = server.url("/api/warp").toString()

        val upperOk = client.postJson(url, "{}", "ua")
        val redirect = client.postJson(url, "{}", "ua")

        assertTrue(upperOk.isSuccess)
        assertEquals("""{"created":true}""", upperOk.getOrThrow())
        assertTrue(redirect.isFailure)
        assertTrue(redirect.exceptionOrNull()?.message?.contains("300") == true)
    }

    @Test
    fun `postJson too large response is failure`() = runTest {
        val tooLarge = "x".repeat(600_000)
        server.enqueue(MockResponse().setResponseCode(200).setBody(tooLarge))
        val url = server.url("/api/warp").toString()
        val result = client.postJson(url, "{}", "ua")
        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("too large") || message.contains("524288"))
    }

    @Test
    fun `postJson max body size is allowed`() = runTest {
        val maxBody = "y".repeat(524_288)
        server.enqueue(MockResponse().setResponseCode(200).setBody(maxBody))
        val url = server.url("/api/warp").toString()
        val result = client.postJson(url, "{}", "ua")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `readBounded accepts exactly max response size`() {
        val maxBody = ByteArray(524_288) { 'x'.code.toByte() }
        val body = readBounded(ByteArrayInputStream(maxBody), 524_288L)
        assertEquals(maxBody.size, body.length)
    }

    @Test
    fun `readBounded throws when response exceeds max`() {
        val over = ByteArray(524_289) { 'x'.code.toByte() }
        assertFailsWith<IOException> {
            readBounded(ByteArrayInputStream(over), 524_288L)
        }
    }

    private fun readBounded(stream: ByteArrayInputStream, maxBytes: Long): String {
        val companion = HttpUrlConnectionClient::class.java
            .getDeclaredField("Companion")
            .apply { isAccessible = true }
            .get(null)
        val method = companion.javaClass.getDeclaredMethod(
            "readBounded",
            java.io.InputStream::class.java,
            Long::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        return method.invoke(companion, stream, maxBytes) as String
    }
}
