package ru.ozero.coresubscriptions

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class OkHttpSubscriptionSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var source: OkHttpSubscriptionSource

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        source = OkHttpSubscriptionSource(timeoutMs = 2_000L)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fetchSuccessReturnsBody() = runTest {
        val body = "servers payload".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(body)))
        server.enqueue(MockResponse().setResponseCode(404))

        val url = server.url("/v1/servers.json").toString()
        val result = source.fetch(url)

        assertIs<SubscriptionFetchResult.Success>(result)
        assertContentEquals(body, result.body)
        assertNull(result.signature)
    }

    @Test
    fun fetchSuccessWithSignature() = runTest {
        val body = "payload".toByteArray()
        val sig = ByteArray(64) { it.toByte() }
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(body)))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(sig)))

        val url = server.url("/v1/servers.json").toString()
        val result = source.fetch(url)

        assertIs<SubscriptionFetchResult.Success>(result)
        assertContentEquals(body, result.body)
        assertContentEquals(sig, result.signature)
    }

    @Test
    fun fetch404ReturnsFailure() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val url = server.url("/v1/missing.json").toString()
        val result = source.fetch(url)

        assertIs<SubscriptionFetchResult.Failure>(result)
        assertEquals(404, result.statusCode)
    }

    @Test
    fun fetchTimeoutReturnsFailure() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("slow")
                .setBodyDelay(5, TimeUnit.SECONDS),
        )

        val url = server.url("/v1/slow.json").toString()
        val result = source.fetch(url)

        assertIs<SubscriptionFetchResult.Failure>(result)
    }

    @Test
    fun fetchConnectionResetReturnsFailure() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val url = server.url("/v1/reset.json").toString()
        val result = source.fetch(url)

        assertIs<SubscriptionFetchResult.Failure>(result)
    }
}
