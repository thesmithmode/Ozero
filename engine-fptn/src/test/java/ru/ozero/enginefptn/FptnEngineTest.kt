package ru.ozero.enginefptn

import android.os.ParcelFileDescriptor
import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunSpec
import ru.ozero.enginescore.Upstream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("LargeClass")
class FptnEngineTest {

    private lateinit var store: InMemoryFptnConfigStore
    private lateinit var engine: FptnEngine

    @BeforeEach
    fun setUp() {
        store = InMemoryFptnConfigStore()
        engine = FptnEngine(store)
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
    fun `engine id is FPTN`() {
        assertEquals(EngineId.FPTN, engine.id)
    }

    @Test
    fun `capabilities require server and do not support upstream socks`() {
        val caps = engine.capabilities
        assertEquals(true, caps.requiresServer)
        assertEquals(false, caps.supportsUpstreamSocks)
        assertEquals(true, caps.supportsTcp)
        assertEquals(true, caps.supportsUdp)
    }

    @Test
    fun `stats starts with zero counters`() = runTest {
        val stats = engine.stats().first()

        assertEquals(0L, stats.bytesIn)
        assertEquals(0L, stats.bytesOut)
        assertEquals(0, stats.activeConnections)
    }

    @Test
    fun `tunSpec exposes blocking full tunnel contract`() = runTest {
        val spec = assertIs<TunSpec>(engine.tunSpec())

        assertEquals("FPTN", spec.sessionName)
        assertEquals(1500, spec.mtu)
        assertEquals(true, spec.blocking)
        assertEquals("10.10.0.1", spec.ipv4Address)
        assertEquals("fd00::1", spec.ipv6Address)
        assertEquals(true, spec.routeAllV4)
        assertEquals(false, spec.routeAllV6)
    }

    @Test
    fun `exitNodeStrategy is unavailable before authenticated start`() = runTest {
        val strategy = engine.exitNodeStrategy(0)

        val unavailable = assertIs<ExitNodeStrategy.Unavailable>(strategy)
        assertTrue(unavailable.reason.contains("FPTN"))
    }

    @Test
    fun `attachTun fails before start`() = runTest {
        val result = engine.attachTun(42)

        val failure = assertIs<TunAttachResult.Failure>(result)
        assertTrue(failure.reason.contains("not started", ignoreCase = true))
    }

    @Test
    fun `awaitReady times out before attachTun creates native handle`() = runTest {
        val result = engine.awaitReady()

        val timeout = assertIs<EnginePlugin.ReadyResult.Timeout>(result)
        assertTrue(timeout.reason.contains("handle", ignoreCase = true))
    }

    @Test
    fun `start with wrong config type returns failure`() = runTest {
        val result = engine.start(EngineConfig.ByeDpi(), Upstream.None)
        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun `start with blank token returns failure`() = runTest {
        val result = engine.start(EngineConfig.Fptn(token = ""), Upstream.None)
        val failure = assertIs<StartResult.Failure>(result)
        assertEquals(true, failure.reason.contains("token", ignoreCase = true))
    }

    @Test
    fun `start with whitespace-only token returns failure`() = runTest {
        val result = engine.start(EngineConfig.Fptn(token = "   "), Upstream.None)
        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun `start with unknown token prefix returns failure`() = runTest {
        val result = engine.start(EngineConfig.Fptn(token = "notfptn:abc"), Upstream.None)
        val failure = assertIs<StartResult.Failure>(result)
        assertEquals(FptnEngine.FPTN_INVALID_TOKEN, failure.reason)
    }

    @Test
    fun `start with malformed base64 returns failure`() = runTest {
        every { Base64.decode(any<String>(), any<Int>()) } throws IllegalArgumentException("bad")
        val result = engine.start(EngineConfig.Fptn(token = "fptn:!!!"), Upstream.None)
        val failure = assertIs<StartResult.Failure>(result)
        assertEquals(FptnEngine.FPTN_INVALID_TOKEN, failure.reason)
    }

    @Test
    fun `start with empty server token returns invalid token before native load`() = runTest {
        val token = java.util.Base64.getEncoder().encodeToString(
            """{"version":1,"username":"u","password":"p","servers":[]}""".toByteArray(),
        )

        val result = engine.start(EngineConfig.Fptn(token = "fptn:$token"), Upstream.None)

        val failure = assertIs<StartResult.Failure>(result)
        assertEquals(FptnEngine.FPTN_INVALID_TOKEN, failure.reason)
    }

    @Test
    fun `start manual selected server absent returns no server failure before native load`() = runTest {
        val result = engine.start(
            EngineConfig.Fptn(
                token = "fptn:${validTokenB64()}",
                autoSelect = false,
                selectedServerName = "Missing",
            ),
            Upstream.None,
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertEquals(FptnEngine.FPTN_NO_SERVER_AVAILABLE, failure.reason)
    }

    @Test
    fun `start authenticates selected server and exposes exit node after success`() = runTest {
        val https = FakeHttpsClient(
            postResponses = ArrayDeque(
                listOf(FptnNativeResponse(200, """{"access_token":"access"}""", "")),
            ),
        )
        engine = FptnEngine(store, wsClient = FakeWebSocketClient(), httpsClient = https)

        val result = engine.start(
            EngineConfig.Fptn(
                token = "fptn:${validTokenB64(host = "127.0.0.1", countryCode = "de")}",
                autoSelect = true,
            ),
            Upstream.None,
        )

        assertIs<StartResult.Success>(result)
        assertEquals(listOf("127.0.0.1"), https.createdHosts)
        assertEquals(listOf(1L), https.destroyedHandles)
        val strategy = assertIs<ExitNodeStrategy.ProviderLabel>(engine.exitNodeStrategy(0))
        assertEquals("127.0.0.1", strategy.ip)
        assertEquals("DE", strategy.countryCode)
    }

    @Test
    fun `start returns token rejected on terminal auth response`() = runTest {
        engine = FptnEngine(
            store,
            wsClient = FakeWebSocketClient(),
            httpsClient = FakeHttpsClient(
                postResponses = ArrayDeque(listOf(FptnNativeResponse(401, "", "unauthorized"))),
            ),
        )

        val result = engine.start(EngineConfig.Fptn(token = "fptn:${validTokenB64()}"), Upstream.None)

        val failure = assertIs<StartResult.Failure>(result)
        assertEquals(FptnEngine.FPTN_TOKEN_REJECTED, failure.reason)
    }

    @Test
    fun `start maps missing access token to api error`() = runTest {
        engine = FptnEngine(
            store,
            wsClient = FakeWebSocketClient(),
            httpsClient = FakeHttpsClient(
                postResponses = ArrayDeque(listOf(FptnNativeResponse(200, """{"ok":true}""", ""))),
            ),
        )

        val result = engine.start(EngineConfig.Fptn(token = "fptn:${validTokenB64()}"), Upstream.None)

        val failure = assertIs<StartResult.Failure>(result)
        assertEquals(FptnEngine.FPTN_API_ERROR, failure.reason)
    }

    @Test
    fun `start maps non success auth response to api error and destroys handle`() = runTest {
        val https = FakeHttpsClient(
            postResponses = ArrayDeque(listOf(FptnNativeResponse(500, "", "server error"))),
        )
        engine = FptnEngine(store, wsClient = FakeWebSocketClient(), httpsClient = https)

        val result = engine.start(EngineConfig.Fptn(token = "fptn:${validTokenB64()}"), Upstream.None)

        val failure = assertIs<StartResult.Failure>(result)
        assertEquals(FptnEngine.FPTN_API_ERROR, failure.reason)
        assertEquals(listOf(1L), https.destroyedHandles)
    }

    @Test
    fun `start maps native post exception and continues to next auto candidate`() = runTest {
        val https = FakeHttpsClient(
            postFailures = ArrayDeque(listOf(IllegalStateException("timed out"))),
            postResponses = ArrayDeque(listOf(FptnNativeResponse(200, """{"access_token":"access-2"}""", ""))),
        )
        engine = FptnEngine(store, wsClient = FakeWebSocketClient(), httpsClient = https)

        val result = engine.start(EngineConfig.Fptn(token = "fptn:${multiServerTokenB64()}"), Upstream.None)

        assertIs<StartResult.Success>(result)
        assertEquals(listOf("203.0.113.1", "127.0.0.1"), https.createdHosts)
        assertEquals(listOf(1L, 2L), https.destroyedHandles)
    }

    @Test
    fun `start tries next auto candidate after non terminal auth failure`() = runTest {
        val https = FakeHttpsClient(
            postResponses = ArrayDeque(
                listOf(
                    FptnNativeResponse(608, "", "timeout"),
                    FptnNativeResponse(200, """{"access_token":"access-2"}""", ""),
                ),
            ),
        )
        engine = FptnEngine(store, wsClient = FakeWebSocketClient(), httpsClient = https)

        val result = engine.start(
            EngineConfig.Fptn(token = "fptn:${multiServerTokenB64()}"),
            Upstream.None,
        )

        assertIs<StartResult.Success>(result)
        assertEquals(listOf("203.0.113.1", "127.0.0.1"), https.createdHosts)
    }

    @Test
    fun `start reports all candidates failed when auto auth never succeeds`() = runTest {
        engine = FptnEngine(
            store,
            wsClient = FakeWebSocketClient(),
            httpsClient = FakeHttpsClient(
                postResponses = ArrayDeque(
                    listOf(
                        FptnNativeResponse(608, "", "timeout"),
                        FptnNativeResponse(500, "", "server error"),
                    ),
                ),
            ),
        )

        val result = engine.start(EngineConfig.Fptn(token = "fptn:${multiServerTokenB64()}"), Upstream.None)

        val failure = assertIs<StartResult.Failure>(result)
        assertEquals("${FptnEngine.FPTN_ALL_CANDIDATES_FAILED}: ${FptnEngine.FPTN_API_ERROR}", failure.reason)
    }

    @Test
    fun `start returns dns failure when authenticated host cannot resolve`() = runTest {
        engine = FptnEngine(
            store,
            wsClient = FakeWebSocketClient(),
            httpsClient = FakeHttpsClient(
                postResponses = ArrayDeque(listOf(FptnNativeResponse(200, """{"access_token":"access"}""", ""))),
            ),
        )

        val result = engine.start(
            EngineConfig.Fptn(token = "fptn:${validTokenB64(host = "invalid.invalid.invalid")}"),
            Upstream.None,
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertEquals(FptnEngine.FPTN_DNS_FAILED, failure.reason)
    }

    @Test
    fun `attachTun fails when authenticated state has no resolved ip or access token`() = runTest {
        engine.setPrivate("_currentServer", server("S1"))

        val noIp = engine.attachTun(42)

        assertEquals("No resolved server IP", assertIs<TunAttachResult.Failure>(noIp).reason)
        engine.setPrivate("_currentServerIp", "127.0.0.1")

        val noToken = engine.attachTun(42)

        assertEquals("No access token", assertIs<TunAttachResult.Failure>(noToken).reason)
    }

    @Test
    fun `attachTun starts websocket callbacks and stop tears native handle down`() = runTest {
        val ws = FakeWebSocketClient()
        val failures = mutableListOf<String>()
        engine = FptnEngine(
            store,
            onEngineFailed = { failures += it },
            wsClient = ws,
            httpsClient = FakeHttpsClient(
                postResponses = ArrayDeque(listOf(FptnNativeResponse(200, """{"access_token":"access"}""", ""))),
            ),
        )
        assertIs<StartResult.Success>(
            engine.start(EngineConfig.Fptn(token = "fptn:${validTokenB64(host = "127.0.0.1")}"), Upstream.None),
        )

        val attach = engine.attachTun(detachedReadWriteFd())

        assertIs<TunAttachResult.Success>(attach)
        assertEquals(listOf("127.0.0.1"), ws.createdServerIps)
        assertEquals(listOf("access"), ws.createdAccessTokens)
        assertEquals(listOf(11L), ws.runHandles)
        assertIs<EnginePlugin.ReadyResult.Ready>(engine.awaitReady())
        ws.onOpen()
        assertEquals(1, engine.stats().first().activeConnections)
        ws.onMessage(byteArrayOf(1, 2, 3))
        ws.onFailure()
        assertEquals(listOf("fptn-ws-reconnect-exhausted"), failures)
        assertEquals(0, engine.stats().first().activeConnections)

        engine.stop()

        assertEquals(listOf(11L), ws.stoppedHandles)
        assertEquals(listOf(11L), ws.destroyedHandles)
    }

    @Test
    fun `attachTun maps native create exception to attach failure`() = runTest {
        val ws = FakeWebSocketClient(createFailure = IllegalStateException("create boom"))
        engine = FptnEngine(
            store,
            wsClient = ws,
            httpsClient = FakeHttpsClient(
                postResponses = ArrayDeque(listOf(FptnNativeResponse(200, """{"access_token":"access"}""", ""))),
            ),
        )
        assertIs<StartResult.Success>(
            engine.start(EngineConfig.Fptn(token = "fptn:${validTokenB64(host = "127.0.0.1")}"), Upstream.None),
        )

        val attach = engine.attachTun(detachedReadWriteFd())

        val failure = assertIs<TunAttachResult.Failure>(attach)
        assertTrue(failure.reason.contains("nativeCreate failed"))
        assertEquals(emptyList(), ws.destroyedHandles)
    }

    @Test
    fun `attachTun destroys handle when native run throws`() = runTest {
        val ws = FakeWebSocketClient(runFailure = IllegalStateException("run boom"))
        engine = FptnEngine(
            store,
            wsClient = ws,
            httpsClient = FakeHttpsClient(
                postResponses = ArrayDeque(listOf(FptnNativeResponse(200, """{"access_token":"access"}""", ""))),
            ),
        )
        assertIs<StartResult.Success>(
            engine.start(EngineConfig.Fptn(token = "fptn:${validTokenB64(host = "127.0.0.1")}"), Upstream.None),
        )

        val attach = engine.attachTun(detachedReadWriteFd())

        val failure = assertIs<TunAttachResult.Failure>(attach)
        assertTrue(failure.reason.contains("nativeRun failed"))
        assertEquals(listOf(11L), ws.destroyedHandles)
    }

    @Test
    fun `start returns native library load failure before auth`() = runTest {
        val https = FakeHttpsClient(
            postResponses = ArrayDeque(listOf(FptnNativeResponse(200, """{"access_token":"access"}""", ""))),
        )
        engine = FptnEngine(
            store,
            wsClient = FakeWebSocketClient(libraryLoaded = false, loadError = "missing lib"),
            httpsClient = https,
        )

        val result = engine.start(EngineConfig.Fptn(token = "fptn:${validTokenB64()}"), Upstream.None)

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("not loaded"))
        assertEquals(emptyList(), https.createdHosts)
    }

    @Test
    fun `start maps native create exception and destroys no auth handle`() = runTest {
        val https = FakeHttpsClient(createFailure = IllegalStateException("resolve failed"))
        engine = FptnEngine(store, wsClient = FakeWebSocketClient(), httpsClient = https)

        val result = engine.start(EngineConfig.Fptn(token = "fptn:${validTokenB64()}"), Upstream.None)

        val failure = assertIs<StartResult.Failure>(result)
        assertEquals(FptnEngine.FPTN_DNS_FAILED, failure.reason)
        assertEquals(emptyList(), https.destroyedHandles)
    }

    @Test
    fun `start manual without selected server returns no server failure before native load`() = runTest {
        val result = engine.start(
            EngineConfig.Fptn(
                token = "fptn:${validTokenB64()}",
                autoSelect = false,
                selectedServerName = null,
            ),
            Upstream.None,
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertEquals(FptnEngine.FPTN_NO_SERVER_AVAILABLE, failure.reason)
    }

    @Test
    fun `stop when not started does not throw`() = runTest {
        engine.stop()
    }

    @Test
    fun `stop is idempotent`() = runTest {
        engine.stop()
        engine.stop()
    }

    @Test
    fun `probe returns failure when store has no token`() = runTest {
        val result = engine.probe()
        assertIs<ProbeResult.Failure>(result)
    }

    @Test
    fun `probe returns failure when token is blank`() = runTest {
        store.inject { it.copy(token = "  ") }
        val result = engine.probe()
        assertIs<ProbeResult.Failure>(result)
    }

    @Test
    fun `probe returns failure when token is invalid`() = runTest {
        every { Base64.decode(any<String>(), any<Int>()) } throws IllegalArgumentException("bad")
        store.inject { it.copy(token = "fptn:garbage") }
        val result = engine.probe()
        assertIs<ProbeResult.Failure>(result)
    }

    @Test
    fun `probe returns success when token has valid servers`() = runTest {
        store.inject { it.copy(token = "fptn:${validTokenB64()}") }
        val result = engine.probe()
        assertIs<ProbeResult.Success>(result)
    }

    @Test
    fun `autoSelect uses every token server as start candidates`() {
        val tokenData = tokenData(
            server("S1"),
            server("S2"),
            server("S3"),
        )

        val result = engine.selectServerCandidates(
            EngineConfig.Fptn(autoSelect = true, selectedServerName = "S2"),
            tokenData,
        )

        assertEquals(listOf("S1", "S2", "S3"), result.map { it.name })
    }

    @Test
    fun `manual selected server is the only candidate`() {
        val tokenData = tokenData(
            server("S1"),
            server("S2"),
            server("S3"),
        )

        val result = engine.selectServerCandidates(
            EngineConfig.Fptn(autoSelect = false, selectedServerName = "S2"),
            tokenData,
        )

        assertEquals(listOf("S2"), result.map { it.name })
    }

    @Test
    fun `manual missing selected server has no fallback candidates`() {
        val tokenData = tokenData(
            server("S1"),
            server("S2"),
        )

        val result = engine.selectServerCandidates(
            EngineConfig.Fptn(autoSelect = false, selectedServerName = "S9"),
            tokenData,
        )

        assertEquals(emptyList(), result)
    }

    @Test
    fun `manual mode without selected server has no candidates`() {
        val tokenData = tokenData(server("S1"))

        val result = engine.selectServerCandidates(
            EngineConfig.Fptn(autoSelect = false, selectedServerName = null),
            tokenData,
        )

        assertEquals(emptyList(), result)
        assertNull(engine.selectServer(EngineConfig.Fptn(autoSelect = false, selectedServerName = null), tokenData))
    }

    @Test
    fun `autoSelect runtime server selection uses only first token server`() {
        val tokenData = tokenData(
            server("S1"),
            server("S2"),
            server("S3"),
        )

        val result = engine.selectServer(
            EngineConfig.Fptn(autoSelect = true, selectedServerName = "S2"),
            tokenData,
        )

        assertEquals("S1", result?.name)
    }

    @Test
    fun `manual runtime server selection does not append fallback candidates`() {
        val tokenData = tokenData(
            server("S1"),
            server("S2"),
            server("S3"),
        )

        val result = engine.selectServer(
            EngineConfig.Fptn(autoSelect = false, selectedServerName = "S2"),
            tokenData,
        )

        assertEquals("S2", result?.name)
    }

    @Test
    fun `manual runtime server selection returns null for unknown selected server`() {
        val tokenData = tokenData(
            server("S1"),
            server("S2"),
        )

        val result = engine.selectServer(
            EngineConfig.Fptn(autoSelect = false, selectedServerName = "S3"),
            tokenData,
        )

        assertNull(result)
    }

    @Test
    fun `manual selected server candidate preserves full server metadata`() {
        val selected = server("S2").copy(
            host = "selected.example.com",
            port = 8443,
            md5Fingerprint = "fingerprint",
            countryCode = "fr",
        )

        val result = engine.selectServerCandidates(
            EngineConfig.Fptn(autoSelect = false, selectedServerName = "S2"),
            tokenData(server("S1"), selected),
        )

        assertEquals(listOf(selected), result)
    }

    @Test
    fun `start runtime path keeps auto fallback inside bounded budget`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/enginefptn/FptnEngine.kt",
        ).readText()
        val startBody = source.substringAfter("override suspend fun start(")
            .substringBefore("override suspend fun attachTun(")

        assertTrue(
            startBody.contains("selectServerCandidates(fptn, tokenData)"),
            "FPTN start must preserve the auto candidate list for multi-server fallback.",
        )
        assertTrue(
            startBody.contains("authenticateFirstAvailable(") &&
                startBody.contains("candidates = candidates"),
            "FPTN auto start must try later token servers when earlier authentication fails.",
        )
        assertTrue(
            startBody.contains("STARTUP_AUTH_BUDGET_MS") &&
                source.contains("AUTO_AUTH_CANDIDATE_TIMEOUT_S") &&
                source.contains("authTimeoutSeconds(") &&
                source.contains("perCandidateMaxTimeoutS") &&
                source.contains("deadlineMs"),
            "FPTN fallback must preserve one bounded startup budget without a health preselect gate.",
        )
        assertFalse(
            source.contains("AUTO_AUTH_MAX_CANDIDATES") ||
                source.contains(".take(AUTO_AUTH_MAX_CANDIDATES)"),
            "FPTN startup auth must not cap auto candidates to a health-preselected top list.",
        )
        assertFalse(
            source.contains("listOf(selected) + data.servers.filterNot"),
            "Manual FPTN selection must not silently append fallback candidates.",
        )
    }

    @Test
    fun `startup auth preserves selected token order before any health order`() {
        val candidates = listOf(
            server("France-1"),
            server("France-2"),
            server("France-3"),
            server("France-4"),
            server("France-5"),
            server("Estonia-1"),
        )

        val ordered = fptnStartupAuthCandidates(candidates)

        assertEquals(
            listOf("France-1", "France-2", "France-3", "France-4", "France-5", "Estonia-1"),
            ordered.map { it.name },
        )
    }

    @Test
    fun `startup auth does not cap auto candidates to health top three`() {
        val candidates = (1..28).map { server("S$it") }

        val ordered = fptnStartupAuthCandidates(candidates)

        assertEquals(28, ordered.size)
        assertEquals(listOf("S1", "S2", "S3", "S4", "S5"), ordered.take(5).map { it.name })
        assertEquals("S28", ordered.last().name)
    }

    @Test
    fun `auto auth candidate timeout stays at baseline five seconds`() {
        assertEquals(5, fptnStartupAuthPerCandidateTimeoutS(candidateCount = 28))
        assertEquals(15, fptnStartupAuthPerCandidateTimeoutS(candidateCount = 1))
        assertFalse(fptnStartupAuthPerCandidateTimeoutS(candidateCount = 28) == 3)
    }

    @Test
    fun `startup auth failure reason remains structured for candidate failures`() {
        val reason = startupFptnFailureReason(
            failures = listOf(
                FptnEngine.FPTN_AUTH_TIMEOUT,
                FptnEngine.FPTN_AUTH_TIMEOUT,
                FptnEngine.FPTN_AUTH_TIMEOUT,
            ),
            candidateCount = 28,
        )

        assertEquals("${FptnEngine.FPTN_ALL_CANDIDATES_FAILED}: ${FptnEngine.FPTN_AUTH_TIMEOUT}", reason)
        assertEquals(
            FptnEngine.FPTN_TOKEN_REJECTED,
            startupFptnFailureReason(listOf(FptnEngine.FPTN_TOKEN_REJECTED), candidateCount = 28),
        )
    }

    @Test
    fun `startup failure reason prioritizes dns api timeout and empty failures`() {
        assertEquals(
            FptnEngine.FPTN_AUTH_TIMEOUT,
            startupFptnFailureReason(emptyList(), candidateCount = 1),
        )
        assertEquals(
            "${FptnEngine.FPTN_ALL_CANDIDATES_FAILED}: ${FptnEngine.FPTN_DNS_FAILED}",
            startupFptnFailureReason(
                listOf(FptnEngine.FPTN_API_ERROR, FptnEngine.FPTN_DNS_FAILED),
                candidateCount = 3,
            ),
        )
        assertEquals(
            FptnEngine.FPTN_API_ERROR,
            startupFptnFailureReason(listOf(FptnEngine.FPTN_API_ERROR), candidateCount = 1),
        )
        assertEquals(
            "${FptnEngine.FPTN_ALL_CANDIDATES_FAILED}: ${FptnEngine.FPTN_AUTH_TIMEOUT}",
            startupFptnFailureReason(
                listOf("unexpected", FptnEngine.FPTN_AUTH_TIMEOUT),
                candidateCount = 2,
            ),
        )
    }

    @Test
    fun `exit node strategy exposes resolved server ip and flag source`() {
        val strategy = fptnExitNodeStrategy(
            server("Berlin").copy(countryCode = "de"),
            serverIp = "198.51.100.70",
        )

        val label = assertIs<ExitNodeStrategy.ProviderLabel>(strategy)
        assertEquals("198.51.100.70", label.ip)
        assertEquals("DE", label.countryCode)
        assertTrue(label.label.isNotBlank())
    }

    @Test
    fun `exit node strategy trims country code and omits blank server ip`() {
        val strategy = fptnExitNodeStrategy(
            server("Fallback").copy(countryCode = " us "),
            serverIp = " ",
            displayLocale = Locale.ENGLISH,
        )

        val label = assertIs<ExitNodeStrategy.ProviderLabel>(strategy)
        assertEquals("United States", label.label)
        assertEquals("US", label.countryCode)
        assertNull(label.ip)
    }

    @Test
    fun `exit node strategy falls back when country code is blank long or numeric`() {
        listOf("", "USA", "1A").forEach { code ->
            val strategy = fptnExitNodeStrategy(
                server("Token-$code").copy(countryCode = code),
                serverIp = null,
                displayLocale = Locale.ENGLISH,
            )

            val label = assertIs<ExitNodeStrategy.ProviderLabel>(strategy)
            assertEquals("Token-$code", label.label)
            assertNull(label.countryCode)
        }
    }

    @Test
    fun `authentication fallback is cancellation cooperative`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/enginefptn/FptnEngine.kt",
        ).readText()
        val fallbackBody = source.substringAfter("private suspend fun authenticateFirstAvailable(")
            .substringBefore("private suspend fun authenticate(")
        val authenticateBody = source.substringAfter("private suspend fun authenticate(")
            .substringBefore("private data class AuthenticatedServer")

        assertTrue(
            fallbackBody.contains("currentCoroutineContext().ensureActive()"),
            "FPTN fallback loop must stop before trying more servers after lifecycle cancellation.",
        )
        assertTrue(
            authenticateBody.contains("currentCoroutineContext().ensureActive()"),
            "FPTN single-server authentication must observe cancellation around native calls.",
        )
        assertTrue(
            authenticateBody.contains("catch (e: CancellationException)") &&
                authenticateBody.contains("throw e"),
            "FPTN authentication must not swallow coroutine cancellation as a generic auth failure.",
        )
    }

    @Test
    fun `websocket receives resolved server ip instead of token host`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/enginefptn/FptnEngine.kt",
        ).readText()
        val attachBody = source.substringAfter("override suspend fun attachTun(")
            .substringBefore("override suspend fun awaitReady()")
        val fallbackBody = source.substringAfter("private suspend fun authenticateFirstAvailable(")
            .substringBefore("private suspend fun authenticate(")
        val resolverBody = source.substringAfter("private suspend fun resolveServerIp(")
            .substringBefore("private data class AuthenticatedServer")

        assertTrue(
            fallbackBody.contains("resolveServerIp(server)") &&
                source.contains("val serverIp: String"),
            "FPTN login success must carry a resolved IPv4 into the WebSocket layer.",
        )
        assertTrue(
            resolverBody.contains("InetAddress.getAllByName(server.host)") &&
                resolverBody.contains("Inet4Address"),
            "FPTN must match upstream ResolveDomain before native WebSocket, which accepts IPv4.",
        )
        assertTrue(
            attachBody.contains("serverIp = serverIp"),
            "nativeCreate must receive resolved serverIp, not raw token host.",
        )
        assertFalse(
            attachBody.contains("serverIp = server.host"),
            "Raw token host can be a DNS name; C++ wrapper parses serverIp as IPv4Address.",
        )
    }

    @Test
    fun `auth failure classifier maps startup failure reasons`() {
        assertEquals(
            FptnEngine.FPTN_TOKEN_REJECTED,
            classifyFptnAuthFailure(FptnNativeResponse(401, "", "")),
        )
        assertEquals(
            FptnEngine.FPTN_TOKEN_REJECTED,
            classifyFptnAuthFailure(FptnNativeResponse(403, "", "")),
        )
        assertEquals(
            FptnEngine.FPTN_AUTH_TIMEOUT,
            classifyFptnAuthFailure(FptnNativeResponse(608, "", "Operation timeout")),
        )
        assertEquals(
            FptnEngine.FPTN_DNS_FAILED,
            classifyFptnAuthFailure(error = "UnknownHostException resolve failed"),
        )
        assertEquals(
            FptnEngine.FPTN_API_ERROR,
            classifyFptnAuthFailure(FptnNativeResponse(500, "", "internal server error")),
        )
    }

    @Test
    fun `auth failure classifier maps text signals without response code`() {
        assertEquals(
            FptnEngine.FPTN_TOKEN_REJECTED,
            classifyFptnAuthFailure(error = "authentication failed"),
        )
        assertEquals(
            FptnEngine.FPTN_TOKEN_REJECTED,
            classifyFptnAuthFailure(body = "invalid token"),
        )
        assertEquals(
            FptnEngine.FPTN_AUTH_TIMEOUT,
            classifyFptnAuthFailure(error = "timed out while reading"),
        )
        assertEquals(
            FptnEngine.FPTN_DNS_FAILED,
            classifyFptnAuthFailure(exceptionName = "UnresolvedAddressException"),
        )
        assertEquals(
            FptnEngine.FPTN_API_ERROR,
            classifyFptnAuthFailure(error = "connection reset"),
        )
    }

    @Test
    fun `auth failure classifier prefers response code over ambiguous text`() {
        assertEquals(
            FptnEngine.FPTN_TOKEN_REJECTED,
            classifyFptnAuthFailure(FptnNativeResponse(403, "timeout", "dns")),
        )
        assertEquals(
            FptnEngine.FPTN_TOKEN_REJECTED,
            classifyFptnAuthFailure(FptnNativeResponse(608, "invalid token", "")),
        )
        assertEquals(
            FptnEngine.FPTN_TOKEN_REJECTED,
            classifyFptnAuthFailure(FptnNativeResponse(502, "forbidden", "")),
        )
    }

    @Test
    fun `buildManualConfig returns null when token blank`() {
        assertNull(engine.buildManualConfig(null))
    }

    @Test
    fun `buildManualConfig returns null when token whitespace only`() {
        store.inject { it.copy(token = "   ") }
        assertNull(engine.buildManualConfig(null))
    }

    @Test
    fun `buildManualConfig returns Fptn config when token set`() {
        store.inject {
            it.copy(
                token = "fptn:abc",
                selectedServerName = "S1",
                bypassMethod = FptnBypassMethod.SNI_REALITY_CHROME_147.strategyName,
                sniDomain = "example.com",
                autoSelect = false,
                reconnectOnNetworkChange = true,
                reconnectOnIpChange = true,
                maxReconnectAttempts = 7,
                reconnectPauseSeconds = 5,
                resetServerOnDisconnect = false,
            )
        }
        val cfg = engine.buildManualConfig(null)
        val fptn = assertIs<EngineConfig.Fptn>(cfg)
        assertEquals("fptn:abc", fptn.token)
        assertEquals("S1", fptn.selectedServerName)
        assertEquals(FptnBypassMethod.SNI_REALITY_CHROME_147.strategyName, fptn.bypassMethod)
        assertEquals("example.com", fptn.sniDomain)
        assertEquals(false, fptn.autoSelect)
        assertEquals(true, fptn.reconnectOnNetworkChange)
        assertEquals(true, fptn.reconnectOnIpChange)
        assertEquals(7, fptn.maxReconnectAttempts)
        assertEquals(5, fptn.reconnectPauseSeconds)
        assertEquals(false, fptn.resetServerOnDisconnect)
    }

    @Test
    fun `buildManualConfig propagates sniDomain`() {
        store.inject { it.copy(token = "fptn:abc", sniDomain = "custom.example.com") }
        val fptn = assertIs<EngineConfig.Fptn>(engine.buildManualConfig(null))
        assertEquals("custom.example.com", fptn.sniDomain)
    }

    @Test
    fun `buildManualConfig propagates default config values when only token set`() {
        store.inject { it.copy(token = "fptn:abc") }

        val fptn = assertIs<EngineConfig.Fptn>(engine.buildManualConfig(null))

        assertEquals(null, fptn.selectedServerName)
        assertEquals(FptnBypassMethod.DEFAULT.strategyName, fptn.bypassMethod)
        assertEquals(FptnConfig.DEFAULT_SNI_DOMAIN, fptn.sniDomain)
        assertEquals(true, fptn.autoSelect)
        assertEquals(true, fptn.reconnectOnNetworkChange)
        assertEquals(false, fptn.reconnectOnIpChange)
        assertEquals(5, fptn.maxReconnectAttempts)
        assertEquals(2, fptn.reconnectPauseSeconds)
        assertEquals(true, fptn.resetServerOnDisconnect)
    }

    @Test
    fun `in memory config store update and flow expose same snapshot`() = runTest {
        store.update {
            it.copy(
                token = "fptn:updated",
                selectedServerName = "S2",
                autoSelect = false,
            )
        }

        assertEquals(store.config().first(), store.currentConfig())
        assertEquals("fptn:updated", store.snapshot.token)
        assertEquals("S2", store.snapshot.selectedServerName)
        assertEquals(false, store.snapshot.autoSelect)
    }

    @Test
    fun `buildManualConfig ignores settings parameter for FPTN-specific config`() {
        store.inject { it.copy(token = "fptn:zzz") }
        val withSettings = engine.buildManualConfig(null)
        val withoutSettings = engine.buildManualConfig(null)
        assertNotNull(withSettings)
        assertEquals(withSettings, withoutSettings)
    }

    @Test
    fun `EngineConfig Fptn default bypassMethod matches enum DEFAULT`() {
        assertEquals(
            FptnBypassMethod.DEFAULT.strategyName,
            EngineConfig.Fptn.DEFAULT_BYPASS_METHOD,
        )
        assertEquals(
            EngineConfig.Fptn.DEFAULT_BYPASS_METHOD,
            EngineConfig.Fptn().bypassMethod,
        )
    }

    @Test
    fun `DEFAULT bypass method strategyName is SNI`() {
        assertEquals("SNI", FptnBypassMethod.DEFAULT.strategyName)
        assertEquals("SNI", EngineConfig.Fptn.DEFAULT_BYPASS_METHOD)
    }

    @Test
    fun `FptnConfig DEFAULT_SNI_DOMAIN is ads x5 ru`() {
        assertEquals("ads.x5.ru", FptnConfig.DEFAULT_SNI_DOMAIN)
    }

    @Test
    fun `EngineConfig Fptn DEFAULT_SNI_DOMAIN is ads x5 ru`() {
        assertEquals("ads.x5.ru", EngineConfig.Fptn.DEFAULT_SNI_DOMAIN)
        assertEquals(EngineConfig.Fptn.DEFAULT_SNI_DOMAIN, EngineConfig.Fptn().sniDomain)
    }

    @Test
    fun `FptnBypassMethod SNI usesSni is true`() {
        assertEquals(true, FptnBypassMethod.SNI.usesSni)
    }

    @Test
    fun `FptnBypassMethod OBFUSCATION usesSni is false`() {
        assertEquals(false, FptnBypassMethod.OBFUSCATION.usesSni)
    }

    @Test
    fun `FptnBypassMethod OBFUSCATION isReality is false`() {
        assertEquals(false, FptnBypassMethod.OBFUSCATION.isReality)
    }

    @Test
    fun `FptnBypassMethod reality variants all have isReality true`() {
        FptnBypassMethod.REALITY_METHODS.forEach { method ->
            assertEquals(true, method.isReality, "Expected ${method.name} isReality=true")
            assertEquals(true, method.usesSni, "Expected ${method.name} usesSni=true")
        }
    }

    @Test
    fun `FptnBypassMethod REALITY_METHODS does not include SNI or OBFUSCATION`() {
        assertEquals(false, FptnBypassMethod.REALITY_METHODS.contains(FptnBypassMethod.SNI))
        assertEquals(false, FptnBypassMethod.REALITY_METHODS.contains(FptnBypassMethod.OBFUSCATION))
    }

    private fun validTokenB64(host: String = "1.2.3.4", countryCode: String = ""): String {
        val json = """{"version":1,"username":"u","password":"p",
            "servers":[{"name":"S1","host":"$host","port":443,"countryCode":"$countryCode"}]}"""
        return java.util.Base64.getEncoder().encodeToString(json.toByteArray())
    }

    private fun multiServerTokenB64(): String {
        val json = """{"version":1,"username":"u","password":"p",
            "servers":[
              {"name":"S1","host":"203.0.113.1","port":443},
              {"name":"S2","host":"127.0.0.1","port":443}
            ]}"""
        return java.util.Base64.getEncoder().encodeToString(json.toByteArray())
    }

    private fun tokenData(vararg servers: FptnServer): FptnTokenData =
        FptnTokenData(
            version = 1,
            username = "u",
            password = "p",
            servers = servers.toList(),
        )

    private fun server(name: String): FptnServer =
        FptnServer(
            name = name,
            host = "$name.example.com",
            port = 443,
            md5Fingerprint = "",
            countryCode = "",
        )

    private fun detachedReadWriteFd(): Int {
        val file = File.createTempFile("fptn-engine-test", ".tun")
        file.deleteOnExit()
        FileOutputStream(file).use { it.write(byteArrayOf(0)) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).detachFd()
    }

    private fun Any.setPrivate(name: String, value: Any?) {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(this, value)
    }

    private class FakeWebSocketClient(
        override val libraryLoaded: Boolean = true,
        override val loadError: String? = null,
        private val createFailure: RuntimeException? = null,
        private val runFailure: RuntimeException? = null,
    ) : FptnWebSocketClient {
        override var onOpen: () -> Unit = {}
        override var onMessage: (ByteArray) -> Unit = {}
        override var onFailure: () -> Unit = {}
        var loadCalls = 0
        val createdServerIps = mutableListOf<String>()
        val createdAccessTokens = mutableListOf<String>()
        val runHandles = mutableListOf<Long>()
        val destroyedHandles = mutableListOf<Long>()
        val stoppedHandles = mutableListOf<Long>()

        override fun loadOnce() {
            loadCalls++
        }

        override fun nativeCreate(
            serverIp: String,
            serverPort: Int,
            tunIpv4: String,
            tunIpv6: String,
            sni: String,
            accessToken: String,
            md5Fingerprint: String,
            censorshipStrategy: String,
        ): Long {
            createFailure?.let { throw it }
            createdServerIps += serverIp
            createdAccessTokens += accessToken
            return 11L
        }

        override fun nativeDestroy(handle: Long) {
            destroyedHandles += handle
        }

        override fun nativeRun(handle: Long): Boolean {
            runFailure?.let { throw it }
            runHandles += handle
            return true
        }

        override fun nativeStop(handle: Long): Boolean {
            stoppedHandles += handle
            return true
        }

        override fun nativeSend(handle: Long, data: ByteArray, length: Long): Boolean = true
        override fun nativeIsStarted(handle: Long): Boolean = true
    }

    private class FakeHttpsClient(
        private val postResponses: ArrayDeque<FptnNativeResponse> = ArrayDeque(),
        private val postFailures: ArrayDeque<RuntimeException> = ArrayDeque(),
        private val createFailure: RuntimeException? = null,
    ) : FptnHttpsClient {
        val createdHosts = mutableListOf<String>()
        val destroyedHandles = mutableListOf<Long>()
        private var nextHandle = 1L

        override fun nativeCreate(
            host: String,
            port: Int,
            sni: String,
            md5Fingerprint: String,
            censorshipStrategy: String,
        ): Long {
            createFailure?.let { throw it }
            createdHosts += host
            return nextHandle++
        }

        override fun nativeDestroy(handle: Long) {
            destroyedHandles += handle
        }

        override fun nativeGet(handle: Long, path: String, timeoutSeconds: Int): FptnNativeResponse =
            error("GET is not used by FptnEngine auth")

        override fun nativePost(
            handle: Long,
            path: String,
            body: String,
            timeoutSeconds: Int,
        ): FptnNativeResponse {
            if (postFailures.isNotEmpty()) throw postFailures.removeFirst()
            return postResponses.removeFirst()
        }
    }
}
