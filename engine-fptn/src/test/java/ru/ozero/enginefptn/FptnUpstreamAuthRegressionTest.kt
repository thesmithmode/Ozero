package ru.ozero.enginefptn

import android.util.Base64
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FptnUpstreamAuthRegressionTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any<Int>()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun `auto selection reaches a healthy endpoint after twenty failed endpoints`() = runTest {
        val https = SelectionHttpsClient { host, _, _ ->
            if (host == "127.0.0.21") {
                FptnNativeResponse(200, """{"access_token":"access"}""", "")
            } else {
                FptnNativeResponse(608, "", "Operation timeout")
            }
        }
        val engine = engine(https)

        val result = engine.start(EngineConfig.Fptn(token = token(21)), Upstream.None)

        assertIs<StartResult.Success>(result)
        assertTrue("127.0.0.21" in https.createdHosts)
    }

    @Test
    fun `auto login allows the upstream ten second request window`() = runTest {
        val https = SelectionHttpsClient { _, timeoutSeconds, _ ->
            if (timeoutSeconds >= 10) {
                FptnNativeResponse(200, """{"access_token":"access"}""", "")
            } else {
                FptnNativeResponse(608, "", "Operation timeout")
            }
        }
        val engine = engine(https)

        val result = engine.start(EngineConfig.Fptn(token = token(1)), Upstream.None)

        assertIs<StartResult.Success>(result)
    }

    @Test
    fun `auto login retries a transient transport failure within the startup budget`() = runTest {
        val https = SelectionHttpsClient { _, _, attempt ->
            if (attempt == 1) {
                FptnNativeResponse(600, "", "connection reset")
            } else {
                FptnNativeResponse(200, """{"access_token":"access"}""", "")
            }
        }
        val engine = engine(https)

        val result = engine.start(EngineConfig.Fptn(token = token(1)), Upstream.None)

        assertIs<StartResult.Success>(result)
        assertEquals(2, https.createdHosts.size)
    }

    private fun engine(https: FptnHttpsClient): FptnEngine {
        val socket = mockk<FptnWebSocketClient>()
        every { socket.loadOnce() } returns Unit
        every { socket.libraryLoaded } returns true
        return FptnEngine(InMemoryFptnConfigStore(), wsClient = socket, httpsClient = https)
    }

    private fun token(serverCount: Int): String {
        val servers = (1..serverCount).joinToString(",") { index ->
            """{"name":"S$index","host":"127.0.0.$index","port":443}"""
        }
        val json = """{"username":"u","password":"p","servers":[$servers]}"""
        return "fptn:" + java.util.Base64.getEncoder().encodeToString(json.toByteArray())
    }

    private class SelectionHttpsClient(
        private val response: (String, Int, Int) -> FptnNativeResponse,
    ) : FptnHttpsClient {
        private val hosts = mutableMapOf<Long, String>()
        private val attempts = mutableMapOf<String, Int>()
        val createdHosts = mutableListOf<String>()
        private var nextHandle = 1L

        override fun nativeCreate(
            host: String,
            port: Int,
            sni: String,
            md5Fingerprint: String,
            censorshipStrategy: String,
        ): Long = synchronized(this) {
            createdHosts += host
            val handle = nextHandle++
            hosts[handle] = host
            handle
        }

        override fun nativeDestroy(handle: Long) {
            synchronized(this) { hosts.remove(handle) }
        }

        override fun nativeGet(handle: Long, path: String, timeoutSeconds: Int): FptnNativeResponse =
            error("Unexpected GET")

        override fun nativePost(
            handle: Long,
            path: String,
            body: String,
            timeoutSeconds: Int,
        ): FptnNativeResponse {
            val (host, attempt) = synchronized(this) {
                val host = hosts.getValue(handle)
                val attempt = attempts.getOrDefault(host, 0) + 1
                attempts[host] = attempt
                host to attempt
            }
            return response(host, timeoutSeconds, attempt)
        }
    }
}
