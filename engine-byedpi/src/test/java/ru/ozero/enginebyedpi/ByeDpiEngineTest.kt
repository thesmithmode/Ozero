package ru.ozero.enginebyedpi

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
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
        assert(caps.localOnly)
        assert(!caps.requiresServer)
        assert(caps.supportsTcp)
        assert(!caps.supportsUdp)
    }

    @Test
    fun startSuccessWhenSocksPortReady() = runTest {
        every { proxy.startProxy(any()) } returns 0
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
    fun startArgsIncludePortFlag() = runTest {
        every { proxy.startProxy(any()) } returns 0
        engine.start(EngineConfig.ByeDpi(socksPort = 1080))
        verify {
            proxy.startProxy(
                match { args -> args.contains("-p") && args.contains("1080") }
            )
        }
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
                hostsMode = ru.ozero.enginescore.settings.HostsMode.DISABLED,
                hosts = listOf("youtube.com"),
            ),
        )
        assertEquals(emptyList(), args)
    }

    @Test
    fun buildHostsArgs_emptyList_returnsEmpty() {
        val args = engine.buildHostsArgs(
            EngineConfig.ByeDpi(
                hostsMode = ru.ozero.enginescore.settings.HostsMode.WHITELIST,
                hosts = emptyList(),
            ),
        )
        assertEquals(emptyList(), args)
    }

    @Test
    fun buildHostsArgs_whitelistMode_addsHflag() {
        val args = engine.buildHostsArgs(
            EngineConfig.ByeDpi(
                hostsMode = ru.ozero.enginescore.settings.HostsMode.WHITELIST,
                hosts = listOf("youtube.com", "discord.com"),
            ),
        )
        assertEquals(listOf("-H:youtube.com discord.com"), args)
    }

    @Test
    fun buildHostsArgs_blacklistMode_addsHflagAndAn() {
        val args = engine.buildHostsArgs(
            EngineConfig.ByeDpi(
                hostsMode = ru.ozero.enginescore.settings.HostsMode.BLACKLIST,
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
                hostsMode = ru.ozero.enginescore.settings.HostsMode.WHITELIST,
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
    fun ipProbeRouteReturnsSocksWhenPortGivenExplicitly() = runTest {
        val route = engine.ipProbeRoute(socksPort = 1080)
        assertIs<ru.ozero.enginescore.IpProbeRoute.Socks>(route)
        assertEquals("127.0.0.1", route.host)
        assertEquals(1080, route.port)
    }

    @Test
    fun ipProbeRouteReturnsDefaultBeforeStartWhenPortZero() = runTest {
        val route = engine.ipProbeRoute(socksPort = 0)
        assertIs<ru.ozero.enginescore.IpProbeRoute.Default>(route)
    }
}
