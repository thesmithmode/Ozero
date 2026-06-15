package ru.ozero.enginefptn

import android.os.ParcelFileDescriptor
import android.util.Base64
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class FptnEngineBranchCoverageTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any<Int>()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>().trim())
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun `awaitReady reports timeout when websocket is never started`() = runTest {
        val engine = FptnEngine(
            InMemoryFptnConfigStore(),
            wsClient = FakeNeverStartedWsClient(),
        )
        engine.setPrivate("_nativeHandle", 11L)

        val result = engine.awaitReady()

        assertIs<EnginePlugin.ReadyResult.Timeout>(result)
    }

    @Test
    fun `stop with non-null pfd closes descriptor and skips native destroy when no active handle`() = runTest {
        val engine = FptnEngine(InMemoryFptnConfigStore())
        val pfd = mockk<ParcelFileDescriptor>(relaxed = true)
        val scope = CoroutineScope(Dispatchers.IO)
        val tunStreams = FptnTunStreams(
            pfd = pfd,
            input = ByteArrayInputStream(ByteArray(0)),
            output = ByteArrayOutputStream(),
        )
        engine.setPrivate("_pfd", tunStreams.pfd)
        engine.setPrivate("tunScope", scope)

        engine.stop()

        verify(exactly = 1) { pfd.close() }
        assertEquals(null, engine.getPrivate("_pfd"))
    }

    @Test
    fun `awaitReady returns ready when websocket handle starts`() = runTest {
        val engine = FptnEngine(InMemoryFptnConfigStore(), wsClient = FakeFastWsClient())
        engine.setPrivate("_nativeHandle", 11L)

        val result = engine.awaitReady()

        assertIs<EnginePlugin.ReadyResult.Ready>(result)
    }

    @Test
    fun `stop with active handle stops and destroys websocket state`() = runTest {
        val pfd = mockk<ParcelFileDescriptor>(relaxed = true)
        val ws = FakeFastWsClient()
        val tunScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val job = tunScope.coroutineContext[Job]
        assertNotNull(job)

        val engine = FptnEngine(InMemoryFptnConfigStore(), wsClient = ws)
        engine.setPrivate("_nativeHandle", 11L)
        engine.setPrivate("_pfd", pfd)
        engine.setPrivate("tunScope", tunScope)

        engine.stop()

        verify(exactly = 1) { pfd.close() }
        assertEquals(listOf(11L), ws.stoppedHandles)
        assertEquals(listOf(11L), ws.destroyedHandles)
        assertEquals(null, engine.getPrivate("_pfd"))
        assertEquals(null, engine.getPrivate("tunScope"))
        assertEquals(0L, engine.getPrivate("_nativeHandle") as Long)
        assertEquals(true, job.isCancelled)
    }

    @Test
    fun `stop with scope without job does not invoke join branch`() = runTest {
        val pfd = mockk<ParcelFileDescriptor>(relaxed = true)
        val engine = FptnEngine(InMemoryFptnConfigStore())
        engine.setPrivate("tunScope", CoroutineScope(Dispatchers.IO))
        engine.setPrivate("_pfd", pfd)

        engine.stop()

        verify(exactly = 1) { pfd.close() }
        assertEquals(null, engine.getPrivate("_pfd"))
        assertEquals(null, engine.getPrivate("tunScope"))
    }

    @Test
    fun `tun streams close closes pfd when provided`() {
        val pfd = mockk<ParcelFileDescriptor>(relaxed = true)
        val streams = FptnTunStreams(
            pfd = pfd,
            input = ByteArrayInputStream(ByteArray(0)),
            output = ByteArrayOutputStream(),
        )

        streams.close()

        verify(exactly = 1) { pfd.close() }
    }

    @Test
    fun `tun streams close handles null pfd and regular stream close paths`() {
        val output = ByteArrayOutputStream()
        val streams = FptnTunStreams(
            pfd = null,
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            output = output,
        )

        streams.close()

        output.write(7)
        assertEquals(1, output.size())
    }

    @Test
    fun `exitNodeStrategy uses resolved ip when available`() = runTest {
        val engine = FptnEngine(InMemoryFptnConfigStore())
        engine.setPrivate("_currentServer", server("Node-1").copy(countryCode = "de"))
        engine.setPrivate("_currentServerIp", "203.0.113.7")

        val strategy = assertIs<ExitNodeStrategy.ProviderLabel>(engine.exitNodeStrategy(0))

        assertEquals("DE", strategy.countryCode)
        assertEquals("203.0.113.7", strategy.ip)
    }

    @Test
    fun `probe handles parser returning empty server list`() = runTest {
        val store = InMemoryFptnConfigStore()
        store.inject { it.copy(token = "fptn:ignored") }
        mockkObject(FptnToken)
        try {
            every { FptnToken.parse(any()) } returns FptnTokenData(
                version = 1,
                username = "u",
                password = "p",
                servers = emptyList(),
            )

            val engine = FptnEngine(store)
            val result = engine.probe()

            assertEquals(ProbeResult.Failure("No servers in token"), result)
        } finally {
            unmockkObject(FptnToken)
        }
    }

    @Test
    fun `classify auth failure treats raw 200 status as API error`() {
        assertEquals(
            FptnEngine.FPTN_API_ERROR,
            classifyFptnAuthFailure(FptnNativeResponse(200, "", "")),
        )
    }

    @Test
    fun `classify auth failure covers code body error and exception signals`() {
        val cases = listOf(
            FptnNativeResponse(401, "", "") to FptnEngine.FPTN_TOKEN_REJECTED,
            FptnNativeResponse(403, "", "") to FptnEngine.FPTN_TOKEN_REJECTED,
            FptnNativeResponse(608, "", "") to FptnEngine.FPTN_AUTH_TIMEOUT,
            FptnNativeResponse(500, "authentication failed", "") to FptnEngine.FPTN_TOKEN_REJECTED,
            FptnNativeResponse(500, "", "timed out") to FptnEngine.FPTN_AUTH_TIMEOUT,
            FptnNativeResponse(500, "resolve failed", "") to FptnEngine.FPTN_DNS_FAILED,
            FptnNativeResponse(429, "", "") to FptnEngine.FPTN_API_ERROR,
        )

        cases.forEach { (response, expected) ->
            assertEquals(expected, classifyFptnAuthFailure(response))
        }
        assertEquals(FptnEngine.FPTN_TOKEN_REJECTED, classifyFptnAuthFailure(error = "Forbidden by token issuer"))
        assertEquals(FptnEngine.FPTN_DNS_FAILED, classifyFptnAuthFailure(exceptionName = "UnknownHostException"))
        assertEquals(FptnEngine.FPTN_AUTH_TIMEOUT, classifyFptnAuthFailure(body = "request timeout while logging in"))
    }

    @Test
    fun `buildManualConfig returns null for blank token and full config for populated store`() {
        val blank = FptnEngine(InMemoryFptnConfigStore())

        assertNull(blank.buildManualConfig(null))

        val populated = InMemoryFptnConfigStore(
            FptnConfig(
                token = "token",
                selectedServerName = "S1",
                bypassMethod = "strategy",
                sniDomain = "sni.example.com",
                autoSelect = false,
                reconnectOnNetworkChange = false,
                reconnectOnIpChange = true,
                maxReconnectAttempts = 9,
                reconnectPauseSeconds = 4,
                resetServerOnDisconnect = false,
            ),
        )
        val config = assertIs<EngineConfig.Fptn>(FptnEngine(populated).buildManualConfig(null))

        assertEquals("token", config.token)
        assertEquals("S1", config.selectedServerName)
        assertEquals("strategy", config.bypassMethod)
        assertEquals("sni.example.com", config.sniDomain)
        assertEquals(false, config.autoSelect)
        assertEquals(false, config.reconnectOnNetworkChange)
        assertEquals(true, config.reconnectOnIpChange)
        assertEquals(9, config.maxReconnectAttempts)
        assertEquals(4, config.reconnectPauseSeconds)
        assertEquals(false, config.resetServerOnDisconnect)
    }

    @Test
    fun `exitNodeStrategy is unavailable before start and omits blank resolved ip`() = runTest {
        val engine = FptnEngine(InMemoryFptnConfigStore())

        assertIs<ExitNodeStrategy.Unavailable>(engine.exitNodeStrategy(0))

        engine.setPrivate("_currentServer", server("Node").copy(countryCode = "fr"))
        engine.setPrivate("_currentServerIp", " ")

        val strategy = assertIs<ExitNodeStrategy.ProviderLabel>(engine.exitNodeStrategy(0))
        assertEquals("FR", strategy.countryCode)
        assertNull(strategy.ip)
    }

    @Test
    fun `stop with no descriptor scope or handle is a no-op reset`() = runTest {
        val engine = FptnEngine(InMemoryFptnConfigStore(), wsClient = FakeFastWsClient())

        engine.stop()

        assertEquals(0L, engine.getPrivate("_nativeHandle") as Long)
        assertNull(engine.getPrivate("_pfd"))
        assertNull(engine.getPrivate("tunScope"))
    }

    @Test
    fun `startup failure reason covers token rejection across single and multi candidates`() {
        assertEquals(
            FptnEngine.FPTN_TOKEN_REJECTED,
            startupFptnFailureReason(
                listOf(FptnEngine.FPTN_API_ERROR, FptnEngine.FPTN_TOKEN_REJECTED),
                candidateCount = 1,
            ),
        )
        assertEquals(
            FptnEngine.FPTN_TOKEN_REJECTED,
            startupFptnFailureReason(
                listOf(FptnEngine.FPTN_DNS_FAILED, FptnEngine.FPTN_TOKEN_REJECTED),
                candidateCount = 5,
            ),
        )
    }

    @Test
    fun `exit node strategy falls back to server name for root display locale edge cases`() {
        listOf("ZZ", "QZ").forEach { code ->
            val strategy = assertIs<ExitNodeStrategy.ProviderLabel>(
                fptnExitNodeStrategy(
                    server = server("Fallback-$code").copy(countryCode = code),
                    serverIp = "",
                    displayLocale = Locale.ROOT,
                ),
            )

            assertEquals("Fallback-$code", strategy.label)
            assertEquals(code, strategy.countryCode)
            assertNull(strategy.ip)
        }
    }

    @Test
    fun `start returns auth timeout when startup authentication is cancelled by timeout`() = runTest {
        val engine = FptnEngine(
            configStore = InMemoryFptnConfigStore(),
            wsClient = FakeFastWsClient(),
            httpsClient = SlowPostHttpsClient(postDelayMs = 16_000L),
        )
        val tokenJson =
            """{"version":1,"username":"u","password":"p","servers":[{"name":"S1","host":"127.0.0.1","port":443}]}"""
        val token = java.util.Base64.getEncoder().encodeToString(tokenJson.toByteArray())
        val result = engine.start(
            EngineConfig.Fptn(
                token = "fptn:$token",
            ),
            Upstream.None,
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertEquals(FptnEngine.FPTN_AUTH_TIMEOUT, failure.reason)
    }

    private class SlowPostHttpsClient(
        private val postDelayMs: Long = 16_000L,
    ) : FptnHttpsClient {
        private var nextHandle = 1L

        override fun nativeCreate(
            host: String,
            port: Int,
            sni: String,
            md5Fingerprint: String,
            censorshipStrategy: String,
        ): Long = nextHandle++

        override fun nativeDestroy(handle: Long) {}

        override fun nativeGet(handle: Long, path: String, timeoutSeconds: Int): FptnNativeResponse {
            error("GET is not used by auth path")
        }

        override fun nativePost(
            handle: Long,
            path: String,
            body: String,
            timeoutSeconds: Int,
        ): FptnNativeResponse {
            runBlocking {
                delay(postDelayMs)
            }
            return FptnNativeResponse(200, """{"access_token":"access"}""", "")
        }
    }

    private class FakeFastWsClient : FptnWebSocketClient {
        override var onOpen: () -> Unit = {}
        override var onMessage: (ByteArray) -> Unit = {}
        override var onFailure: () -> Unit = {}
        override fun loadOnce() = Unit
        override val libraryLoaded: Boolean = true
        override val loadError: String? = null
        override fun nativeCreate(
            serverIp: String,
            serverPort: Int,
            tunIpv4: String,
            tunIpv6: String,
            sni: String,
            accessToken: String,
            md5Fingerprint: String,
            censorshipStrategy: String,
        ): Long = 11L
        val stoppedHandles = mutableListOf<Long>()
        val destroyedHandles = mutableListOf<Long>()

        override fun nativeStop(handle: Long): Boolean {
            stoppedHandles += handle
            return true
        }

        override fun nativeDestroy(handle: Long) {
            destroyedHandles += handle
        }

        override fun nativeRun(handle: Long): Boolean = true
        override fun nativeSend(handle: Long, data: ByteArray, length: Long): Boolean = true
        override fun nativeIsStarted(handle: Long): Boolean = true
    }

    private fun Any.setPrivate(name: String, value: Any?) {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(this, value)
    }

    private fun Any.getPrivate(name: String): Any? {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this)
    }

    private class FakeNeverStartedWsClient : FptnWebSocketClient {
        override var onOpen: () -> Unit = {}
        override var onMessage: (ByteArray) -> Unit = {}
        override var onFailure: () -> Unit = {}
        override fun loadOnce() = Unit
        override val libraryLoaded: Boolean = true
        override val loadError: String? = null
        override fun nativeCreate(
            serverIp: String,
            serverPort: Int,
            tunIpv4: String,
            tunIpv6: String,
            sni: String,
            accessToken: String,
            md5Fingerprint: String,
            censorshipStrategy: String,
        ): Long = 1L

        override fun nativeDestroy(handle: Long) = Unit
        override fun nativeRun(handle: Long): Boolean = true
        override fun nativeStop(handle: Long): Boolean = true
        override fun nativeSend(handle: Long, data: ByteArray, length: Long): Boolean = true
        override fun nativeIsStarted(handle: Long): Boolean = false
    }

    private fun server(name: String): FptnServer = FptnServer(
        name = name,
        host = "127.0.0.1",
        port = 443,
        md5Fingerprint = "",
        countryCode = "",
    )
}
