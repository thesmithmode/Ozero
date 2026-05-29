package ru.ozero.enginebyedpi

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.IpProbeRoute
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.HostsMode
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ByeDpiEngineTest {
    private lateinit var proxy: ByeDpiProxy
    private lateinit var engine: ByeDpiEngine
    private lateinit var proxyRunning: CountDownLatch

    @BeforeEach
    fun setUp() {
        proxy = mockk(relaxed = true)
        mockkObject(ByeDpiProxy.Companion)
        every { ByeDpiProxy.loadOnce() } just runs
        every { ByeDpiProxy.libraryLoaded } returns true
        every { ByeDpiProxy.loadError } returns null
        engine = ByeDpiEngine(proxy, socksProbe = { _, _, _ -> 1L })
        proxyRunning = CountDownLatch(1)
    }

    @AfterEach
    fun tearDown() {
        proxyRunning.countDown()
        unmockkObject(ByeDpiProxy.Companion)
    }

    @Test
    fun engineIdIsByeDpi() {
        assertEquals(EngineId.BYEDPI, engine.id)
    }

    @Test
    fun capabilitiesLocalOnly() {
        val caps = engine.capabilities
        assertTrue(caps.localOnly)
        assertFalse(caps.requiresServer)
        assertTrue(caps.supportsTcp)
        assertFalse(caps.supportsUdp)
    }

    @Test
    fun `stopTimeout covers bounded native drain window`() {
        assertTrue(
            engine.stopTimeoutMs() > EnginePlugin.DEFAULT_STOP_TIMEOUT_MS,
            "ChainOrchestrator must not cancel ByeDPI stop before the bounded forceClose drain finishes",
        )
        assertTrue(
            engine.stopTimeoutMs() < 4_000L,
            "ByeDPI stop timeout must stay below ShutdownCoordinator parallel stop budget",
        )
    }

    @Test
    fun startSuccessWhenSocksPortReady() = runTest {
        every { proxy.startProxy(any()) } answers {
            proxyRunning.await()
            0
        }
        val result = engine.start(EngineConfig.ByeDpi(socksPort = 1080))
        assertIs<StartResult.Success>(result)
        assertEquals(1080, result.socksPort)
    }

    @Test
    fun startFailureWhenSocksPortNeverOpens() = runTest {
        val failEngine = ByeDpiEngine(
            proxy,
            socksProbe = { _, _, _ -> throw IOException("refused") },
            readyTotalTimeoutMs = 500,
        )
        every { proxy.startProxy(any()) } returns -1
        val result = failEngine.start(EngineConfig.ByeDpi(socksPort = 19998))
        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun startArgsIncludePortFlag() {
        val args = engine.buildArgs(EngineConfig.ByeDpi(socksPort = 1080))
        assertTrue(args.contains("-p"))
        assertTrue(args.contains("1080"))
    }

    @Test
    fun stopCallsJniStopProxy() = runTest {
        every { proxy.startProxy(any()) } returns 0
        engine.start(EngineConfig.ByeDpi(socksPort = 1080))
        engine.stop()
        verify { proxy.stopProxy() }
    }

    @Test
    fun probeFailsWhenEngineNotStarted() = runTest {
        val result = engine.probe()
        assertIs<ProbeResult.Failure>(result)
    }

    @Test
    fun probeSuccessWhenSocketListens() = runTest {
        every { proxy.startProxy(any()) } answers {
            proxyRunning.await()
            0
        }
        engine.start(EngineConfig.ByeDpi(socksPort = 1080))
        val result = engine.probe()
        assertIs<ProbeResult.Success>(result)
    }

    @Test
    fun probeFailsWhenNoSocketListening() = runTest {
        var callCount = 0
        val localEngine = ByeDpiEngine(
            proxy,
            socksProbe = { _, _, _ -> if (++callCount == 1) 1L else throw IOException("refused") },
        )
        every { proxy.startProxy(any()) } answers {
            proxyRunning.await()
            0
        }
        localEngine.start(EngineConfig.ByeDpi(socksPort = 1080))
        val result = localEngine.probe()
        assertIs<ProbeResult.Failure>(result)
    }

    @Test
    fun probeFailsAfterStartFailure() = runTest {
        val failEngine = ByeDpiEngine(
            proxy,
            socksProbe = { _, _, _ -> throw IOException("refused") },
            readyTotalTimeoutMs = 500,
        )
        every { proxy.startProxy(any()) } returns -1
        failEngine.start(EngineConfig.ByeDpi(socksPort = 12345))
        val result = failEngine.probe()
        assertIs<ProbeResult.Failure>(result)
        assertEquals("движок не запущен", result.reason)
    }

    @Test
    fun buildArgs_noUpstreamFlag_terminalProxy() {
        val args = engine.buildArgs(EngineConfig.ByeDpi(socksPort = 1080)).toList()
        assertTrue(
            args.none { it == "-x" || it.startsWith("--proxy") },
            "ByeDPI terminal proxy — никаких upstream флагов в args: $args",
        )
    }

    @Test
    fun buildHostsArgs_disabledMode_returnsEmpty() {
        val args = engine.buildHostsArgs(
            EngineConfig.ByeDpi(
                hostsMode = HostsMode.DISABLED,
                hosts = listOf("youtube.com"),
            ),
        )
        assertEquals(emptyList(), args)
    }

    @Test
    fun buildHostsArgs_emptyList_returnsEmpty() {
        val args = engine.buildHostsArgs(
            EngineConfig.ByeDpi(
                hostsMode = HostsMode.WHITELIST,
                hosts = emptyList(),
            ),
        )
        assertEquals(emptyList(), args)
    }

    @Test
    fun buildHostsArgs_whitelistMode_addsHflag() {
        val args = engine.buildHostsArgs(
            EngineConfig.ByeDpi(
                hostsMode = HostsMode.WHITELIST,
                hosts = listOf("youtube.com", "discord.com"),
            ),
        )
        assertEquals(listOf("-H:youtube.com discord.com"), args)
    }

    @Test
    fun buildHostsArgs_blacklistMode_addsHflagAndAn() {
        val args = engine.buildHostsArgs(
            EngineConfig.ByeDpi(
                hostsMode = HostsMode.BLACKLIST,
                hosts = listOf("ads.example.com"),
            ),
        )
        assertEquals(listOf("-H:ads.example.com", "-An"), args)
    }

    @Test
    fun buildArgs_includesHostsArgsAtEnd() {
        val args = engine.buildArgs(
            EngineConfig.ByeDpi(
                socksPort = 1080,
                hostsMode = HostsMode.WHITELIST,
                hosts = listOf("youtube.com"),
            ),
        ).toList()
        assertTrue(args.contains("-H:youtube.com"))
    }

    @Test
    fun capabilitiesSupportsUpstreamSocksFalse() {
        assertEquals(false, engine.capabilities.supportsUpstreamSocks, "ByeDPI = terminal proxy")
    }

    @Test
    fun startRejectsSocks5Upstream_terminalProxy() = runTest {
        every { proxy.startProxy(any()) } returns 0
        assertFailsWith<IllegalArgumentException> {
            engine.start(
                EngineConfig.ByeDpi(socksPort = 1080),
                Upstream.Socks5("127.0.0.1", 9050),
            )
        }
    }

    @Test
    fun startRejectsHttpUpstream_terminalProxy() = runTest {
        every { proxy.startProxy(any()) } returns 0
        assertFailsWith<IllegalArgumentException> {
            engine.start(
                EngineConfig.ByeDpi(socksPort = 1080),
                Upstream.Http("proxy.example", 8080),
            )
        }
    }

    @Test
    fun startWithBlankArgsDoesNotPassEmptyToken() = runTest {
        every { proxy.startProxy(any()) } returns 0
        engine.start(EngineConfig.ByeDpi(args = "", socksPort = 1080))
        verify {
            proxy.startProxy(
                match { args -> args.none { it.isEmpty() } },
            )
        }
    }

    @Test
    fun buildArgs_doesNotPrependProgramName_nativeLayerAlreadyInsertsByedpiArgv0() {
        val args = engine.buildArgs(EngineConfig.ByeDpi(socksPort = 1080)).toList()
        assertTrue(
            args.firstOrNull() == "--ip",
            "buildArgs НЕ должен prepend'ить 'ciadpi'/'byedpi' — native-lib.c уже ставит argv[0]='byedpi'. " +
                "Double-prepend → getopt парсит 'ciadpi' как positional, первый реальный флаг (--ip) " +
                "теряется, SOCKS слушает на чужом адресе → traffic IDLE. Fact: $args",
        )
        assertTrue(
            args.none { it == "ciadpi" || it == "byedpi" },
            "argv не должен содержать program-name строк — native слой их вставит сам. Fact: $args",
        )
        assertTrue(args.contains("--ip"), "buildArgs должен передавать --ip bind-address: $args")
    }

    @Test
    fun ipProbeRouteReturnsSocksWhenPortGivenExplicitly() = runTest {
        val route = engine.ipProbeRoute(socksPort = 1080)
        assertIs<IpProbeRoute.Socks>(route)
        assertEquals("127.0.0.1", route.host)
        assertEquals(1080, route.port)
    }

    @Test
    fun ipProbeRouteReturnsUnavailableBeforeStartWhenPortZero() = runTest {
        val route = engine.ipProbeRoute(socksPort = 0)
        assertIs<IpProbeRoute.Unavailable>(route)
    }

    @Test
    fun exitNodeStrategyReturnsSocksWhenPortGivenExplicitly() = runTest {
        val strategy = engine.exitNodeStrategy(socksPort = 1080)
        assertIs<ExitNodeStrategy.ViaSocks>(strategy)
        assertEquals("127.0.0.1", strategy.host)
        assertEquals(1080, strategy.port)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `start failure with main returning -1 must forceClose to reset upstream server_fd`() = runTest {
        val failEngine = ByeDpiEngine(
            proxy,
            socksProbe = { _, _, _ -> throw IOException("refused") },
            readyTotalTimeoutMs = 200,
            testDispatcherOverride = UnconfinedTestDispatcher(testScheduler),
        )
        every { proxy.startProxy(any()) } returns -1
        val result = failEngine.start(EngineConfig.ByeDpi(socksPort = 19001))
        assertIs<StartResult.Failure>(result)
        coVerify(exactly = 0) { proxy.stopProxy() }
        coVerify(atLeast = 1) { proxy.forceClose() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `start failure clears upstream server_fd so next start can bind same port`() = runTest {
        var callCount = 0
        val recProxy = mockk<ByeDpiProxy>(relaxed = true)
        every { recProxy.startProxy(any()) } answers {
            if (callCount++ == 0) -1 else 0
        }
        val recEngine = ByeDpiEngine(
            recProxy,
            socksProbe = { _, _, _ ->
                if (callCount == 1) throw IOException("refused") else 1L
            },
            readyTotalTimeoutMs = 300,
            testDispatcherOverride = UnconfinedTestDispatcher(testScheduler),
        )
        val first = recEngine.start(EngineConfig.ByeDpi(socksPort = 1080))
        assertIs<StartResult.Failure>(first)
        coVerify(atLeast = 1) { recProxy.forceClose() }
        val second = recEngine.start(EngineConfig.ByeDpi(socksPort = 1080))
        assertIs<StartResult.Success>(second)
    }

    @Test
    fun `stop forceClose before waiting native job — server_fd reset guarantees clean next start`() = runTest {
        every { proxy.startProxy(any()) } returns 0
        engine.start(EngineConfig.ByeDpi(socksPort = 1080))
        engine.stop()
        coVerifyOrder {
            proxy.stopProxy()
            proxy.forceClose()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `start pre-flight forceClose runs even when previous job already completed`() = runTest {
        val recProxy = mockk<ByeDpiProxy>(relaxed = true)
        every { recProxy.startProxy(any()) } returns -1
        val recEngine = ByeDpiEngine(
            recProxy,
            socksProbe = { _, _, _ -> throw IOException("refused") },
            readyTotalTimeoutMs = 100,
            testDispatcherOverride = UnconfinedTestDispatcher(testScheduler),
        )
        val first = recEngine.start(EngineConfig.ByeDpi(socksPort = 1080))
        assertIs<StartResult.Failure>(first)
        clearMocks(recProxy, answers = false)
        every { recProxy.startProxy(any()) } returns -1
        val second = recEngine.start(EngineConfig.ByeDpi(socksPort = 1080))
        assertIs<StartResult.Failure>(second)
        coVerify(atLeast = 1) { recProxy.forceClose() }
    }

    @Test
    fun startFailureFastWhenProxyDiesImmediately() = runTest {
        val failEngine = ByeDpiEngine(
            proxy,
            socksProbe = { _, _, _ -> throw IOException("refused") },
            readyTotalTimeoutMs = 5_000,
        )
        every { proxy.startProxy(any()) } returns -1
        val t0 = System.currentTimeMillis()
        val result = failEngine.start(EngineConfig.ByeDpi(socksPort = 19002))
        val elapsed = System.currentTimeMillis() - t0
        assertIs<StartResult.Failure>(result)
        assertTrue(elapsed < 2_000, "Должен завершиться быстро когда прокси умер: elapsed=${elapsed}ms")
    }

    @Test
    fun `start — forceClose когда stopProxy не помог + второй join ждёт нативный поток`() = runTest {
        val proxyStarted = CountDownLatch(1)
        val nativeThreadExit = CountDownLatch(1)
        val orderedProxy: ByeDpiProxy = mockk(relaxed = true)
        mockkObject(ByeDpiProxy.Companion)
        every { ByeDpiProxy.loadOnce() } just runs
        every { ByeDpiProxy.libraryLoaded } returns true
        every { orderedProxy.startProxy(any()) } answers {
            proxyStarted.countDown()
            nativeThreadExit.await()
            0
        }
        every { orderedProxy.forceClose() } answers {
            nativeThreadExit.countDown()
            0
        }
        val eng = ByeDpiEngine(orderedProxy, socksProbe = { _, _, _ -> 1L })
        eng.start(EngineConfig.ByeDpi(socksPort = 1080))
        proxyStarted.await()
        every { orderedProxy.startProxy(any()) } returns 0
        val result = eng.start(EngineConfig.ByeDpi(socksPort = 1081))
        assertIs<StartResult.Success>(result)
        coVerify(atLeast = 1) { orderedProxy.forceClose() }
        unmockkObject(ByeDpiProxy.Companion)
    }

    @Test
    fun `startProxyWithRecovery — normal path skips emergencyReset`() = runTest {
        val startCalled = CountDownLatch(1)
        val normalProxy = mockk<ByeDpiProxy>(relaxed = true)
        every { normalProxy.startProxy(any()) } answers {
            startCalled.countDown()
            proxyRunning.await()
            0
        }
        val normalEngine = ByeDpiEngine(
            normalProxy,
            socksProbe = { _, _, _ ->
                startCalled.await()
                1L
            },
        )
        val result = normalEngine.start(EngineConfig.ByeDpi(socksPort = 1080))
        assertIs<StartResult.Success>(result)
        verify(exactly = 0) { normalProxy.emergencyReset() }
    }

    @Test
    fun `startProxyWithRecovery — guard busy calls emergencyReset then retries start`() = runTest {
        val secondCallStarted = CountDownLatch(1)
        var callCount = 0
        val recProxy = mockk<ByeDpiProxy>(relaxed = true)
        every { recProxy.startProxy(any()) } answers {
            if (callCount++ == 0) {
                -2
            } else {
                secondCallStarted.countDown()
                proxyRunning.await()
                0
            }
        }
        every { recProxy.emergencyReset() } returns 1
        val recEngine = ByeDpiEngine(
            recProxy,
            socksProbe = { _, _, _ ->
                secondCallStarted.await()
                1L
            },
        )
        val result = recEngine.start(EngineConfig.ByeDpi(socksPort = 1080))
        assertIs<StartResult.Success>(result)
        verify(exactly = 1) { recProxy.emergencyReset() }
        verify(exactly = 2) { recProxy.startProxy(any()) }
    }

    @Test
    fun `startProxyWithRecovery — guard busy retry fails returns Failure`() = runTest {
        var callCount = 0
        val recProxy = mockk<ByeDpiProxy>(relaxed = true)
        every { recProxy.startProxy(any()) } answers {
            if (callCount++ == 0) -2 else -1
        }
        every { recProxy.emergencyReset() } returns 1
        val failEngine = ByeDpiEngine(
            recProxy,
            socksProbe = { _, _, _ -> throw IOException("refused") },
            readyTotalTimeoutMs = 300,
        )
        val result = failEngine.start(EngineConfig.ByeDpi(socksPort = 1080))
        assertIs<StartResult.Failure>(result)
        verify(exactly = 1) { recProxy.emergencyReset() }
        verify(exactly = 2) { recProxy.startProxy(any()) }
    }

    @Test
    fun `startProxyWithRecovery — emergencyReset throws still retries startProxy`() = runTest {
        val secondCallStarted = CountDownLatch(1)
        var callCount = 0
        val recProxy = mockk<ByeDpiProxy>(relaxed = true)
        every { recProxy.startProxy(any()) } answers {
            if (callCount++ == 0) {
                -2
            } else {
                secondCallStarted.countDown()
                proxyRunning.await()
                0
            }
        }
        every { recProxy.emergencyReset() } throws RuntimeException("reset failed")
        val recEngine = ByeDpiEngine(
            recProxy,
            socksProbe = { _, _, _ ->
                secondCallStarted.await()
                1L
            },
        )
        val result = recEngine.start(EngineConfig.ByeDpi(socksPort = 1080))
        assertIs<StartResult.Success>(result)
        verify(exactly = 2) { recProxy.startProxy(any()) }
    }

    @Test
    fun startStopsOldProxyBeforeLaunchingNew() = runTest {
        val firstLatch = CountDownLatch(1)
        val blockingProxy: ByeDpiProxy = mockk(relaxed = true)
        mockkObject(ByeDpiProxy.Companion)
        every { ByeDpiProxy.loadOnce() } just runs
        every { ByeDpiProxy.libraryLoaded } returns true
        every { blockingProxy.startProxy(any()) } answers {
            firstLatch.await()
            0
        }
        every { blockingProxy.stopProxy() } answers {
            firstLatch.countDown()
            0
        }
        val eng = ByeDpiEngine(blockingProxy, socksProbe = { _, _, _ -> 1L })
        eng.start(EngineConfig.ByeDpi(socksPort = 1080))
        val result = eng.start(EngineConfig.ByeDpi(socksPort = 1080))
        assertIs<StartResult.Success>(result)
        coVerify(atLeast = 1) { blockingProxy.stopProxy() }
        unmockkObject(ByeDpiProxy.Companion)
    }

    @Test
    fun `start rotates proxy lane when previous native job keeps proxy dispatcher occupied`() =
        assertTimeoutPreemptively(Duration.ofSeconds(15)) {
            val firstNativeEntered = CountDownLatch(1)
            val firstNativeExit = CountDownLatch(1)
            val calls = AtomicInteger(0)
            val blockingProxy: ByeDpiProxy = mockk(relaxed = true)
            mockkObject(ByeDpiProxy.Companion)
            every { ByeDpiProxy.loadOnce() } just runs
            every { ByeDpiProxy.libraryLoaded } returns true
            every { blockingProxy.startProxy(any()) } answers {
                when (calls.getAndIncrement()) {
                    0 -> {
                        firstNativeEntered.countDown()
                        firstNativeExit.await(10, TimeUnit.SECONDS)
                        0
                    }
                    1 -> ByeDpiEngine.JNI_GUARD_BUSY
                    else -> 0
                }
            }
            every { blockingProxy.stopProxy() } returns 0
            every { blockingProxy.forceClose() } returns 0
            every { blockingProxy.emergencyReset() } returns 1
            val eng = ByeDpiEngine(
                blockingProxy,
                socksProbe = { _, port, _ ->
                    if (port == 1080) {
                        assertTrue(firstNativeEntered.await(5, TimeUnit.SECONDS))
                    }
                    1L
                },
            )
            runBlocking {
                try {
                    assertIs<StartResult.Success>(eng.start(EngineConfig.ByeDpi(socksPort = 1080)))
                    assertIs<StartResult.Success>(eng.start(EngineConfig.ByeDpi(socksPort = 1081)))
                } finally {
                    firstNativeExit.countDown()
                    unmockkObject(ByeDpiProxy.Companion)
                }
            }
            verify(exactly = 1) { blockingProxy.emergencyReset() }
            verify(exactly = 3) { blockingProxy.startProxy(any()) }
        }

    @Test
    fun `start after wedged stop rotates proxy lane even when job ref was cleared`() =
        assertTimeoutPreemptively(Duration.ofSeconds(15)) {
            val firstNativeEntered = CountDownLatch(1)
            val firstNativeExit = CountDownLatch(1)
            val calls = AtomicInteger(0)
            val blockingProxy: ByeDpiProxy = mockk(relaxed = true)
            mockkObject(ByeDpiProxy.Companion)
            every { ByeDpiProxy.loadOnce() } just runs
            every { ByeDpiProxy.libraryLoaded } returns true
            every { blockingProxy.startProxy(any()) } answers {
                when (calls.getAndIncrement()) {
                    0 -> {
                        firstNativeEntered.countDown()
                        firstNativeExit.await(10, TimeUnit.SECONDS)
                        0
                    }
                    1 -> ByeDpiEngine.JNI_GUARD_BUSY
                    else -> 0
                }
            }
            every { blockingProxy.stopProxy() } returns 0
            every { blockingProxy.forceClose() } returns 0
            every { blockingProxy.emergencyReset() } returns 1
            val eng = ByeDpiEngine(
                blockingProxy,
                socksProbe = { _, port, _ ->
                    if (port == 1080) {
                        assertTrue(firstNativeEntered.await(5, TimeUnit.SECONDS))
                    }
                    1L
                },
            )
            runBlocking {
                try {
                    assertIs<StartResult.Success>(eng.start(EngineConfig.ByeDpi(socksPort = 1080)))
                    eng.stop()
                    assertIs<StartResult.Success>(eng.start(EngineConfig.ByeDpi(socksPort = 1081)))
                } finally {
                    firstNativeExit.countDown()
                    unmockkObject(ByeDpiProxy.Companion)
                }
            }
            verify(exactly = 1) { blockingProxy.emergencyReset() }
            verify(exactly = 3) { blockingProxy.startProxy(any()) }
        }

    @Test
    fun `autoRotatePort skips ports occupied by another app — sentinel for port-conflict regression`() = runTest {
        val busyPorts = setOf(ByeDpiEngine.PORT_ROTATION_BASE, ByeDpiEngine.PORT_ROTATION_BASE + 1)
        val conflictEngine = ByeDpiEngine(
            proxy,
            socksProbe = { _, _, _ -> 1L },
            portFreeChecker = { port -> port !in busyPorts },
        )
        every { proxy.startProxy(any()) } answers {
            proxyRunning.await()
            0
        }
        val result = conflictEngine.start(EngineConfig.ByeDpi(socksPort = ByeDpiEngine.AUTO_ROTATE_PORT))
        assertIs<StartResult.Success>(result)
        val assignedPort = result.socksPort
        assertTrue(
            assignedPort !in busyPorts,
            "Движок должен пропускать занятые порты $busyPorts, получил $assignedPort",
        )
        assertTrue(
            assignedPort >= ByeDpiEngine.PORT_ROTATION_BASE,
            "Порт должен быть в диапазоне rotation или OS-ephemeral: $assignedPort",
        )
    }
}
