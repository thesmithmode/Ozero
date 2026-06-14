package ru.ozero.enginewarp

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `postJson возвращает тело ответа при HTTP 200`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val url = server.url("/api/warp").toString()
        val result = client.postJson(url, "{}", "test-agent")
        assertTrue(result.isSuccess)
        assertEquals("""{"ok":true}""", result.getOrThrow())
    }

    @Test
    fun `postJson отправляет POST с Content-Type application-json`() = runTest {
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
    fun `postJson возвращает failure при HTTP 500`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))
        val url = server.url("/api/warp").toString()
        val result = client.postJson(url, "{}", "ua")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun `postJson возвращает failure при HTTP 404`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        val url = server.url("/api/warp").toString()
        val result = client.postJson(url, "{}", "ua")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("404") == true)
    }

    @Test
    fun `postJson возвращает failure при недоступном хосте`() = runTest {
        val result = client.postJson("http://127.0.0.1:1", "{}", "ua")
        assertTrue(result.isFailure)
    }

    @Test
    fun `postJson возвращает пустую строку при пустом теле 200`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val url = server.url("/api/warp").toString()
        val result = client.postJson(url, "{}", "ua")
        assertTrue(result.isSuccess)
        assertEquals("", result.getOrThrow())
    }

    @Test
    fun `postJson принимает 201 как успешный ответ`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"created":true}"""))
        val url = server.url("/api/warp").toString()
        val result = client.postJson(url, "{}", "ua")
        assertTrue(result.isSuccess)
        assertEquals("""{"created":true}""", result.getOrThrow())
    }

    @Test
    fun `postJson accepts 299 and rejects 300 boundary responses`() = runTest {
        server.enqueue(MockResponse().setResponseCode(299).setBody("upper-ok"))
        server.enqueue(MockResponse().setResponseCode(300).setBody("redirect"))
        val url = server.url("/api/warp").toString()

        val upperOk = client.postJson(url, "{}", "ua")
        val redirect = client.postJson(url, "{}", "ua")

        assertTrue(upperOk.isSuccess)
        assertEquals("upper-ok", upperOk.getOrThrow())
        assertTrue(redirect.isFailure)
        assertTrue(redirect.exceptionOrNull()?.message?.contains("300") == true)
    }

    @Test
    fun `postJson отклоняет ответ больше 512KB чтобы предотвратить OOM`() = runTest {
        val tooLarge = "x".repeat(600_000)
        server.enqueue(MockResponse().setResponseCode(200).setBody(tooLarge))
        val url = server.url("/api/warp").toString()
        val result = client.postJson(url, "{}", "ua")
        assertTrue(result.isFailure, "ответ >512KB обязан давать failure (DoS prevention)")
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(
            message.contains("too large") || message.contains("524288"),
            "сообщение об ошибке должно указывать на превышение лимита: $message",
        )
    }

    @Test
    fun `postJson принимает ответ 512KB ровно (пограничное значение)`() = runTest {
        val maxBody = "y".repeat(524_288)
        server.enqueue(MockResponse().setResponseCode(200).setBody(maxBody))
        val url = server.url("/api/warp").toString()
        val result = client.postJson(url, "{}", "ua")
        assertTrue(result.isSuccess, "ответ ровно 512KB должен проходить (граница включительно)")
    }
}
