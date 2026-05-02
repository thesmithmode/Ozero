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
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ByeDpiEngineTest {
    private lateinit var proxy: ByeDpiProxy
    private lateinit var engine: ByeDpiEngine

    @BeforeEach
    fun setUp() {
        proxy = mockk(relaxed = true)
        mockkObject(ByeDpiProxy.Companion)
        every { ByeDpiProxy.loadOnce() } just runs
        every { ByeDpiProxy.libraryLoaded } returns true
        every { ByeDpiProxy.loadError } returns null
        engine = ByeDpiEngine(proxy)
    }

    @AfterEach
    fun tearDown() {
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
        val server = ServerSocket(0)
        val port = server.localPort
        server.acceptSocks5InBackground()
        try {
            every { proxy.startProxy(any()) } answers {
                Thread.sleep(60_000)
                0
            }
            val result = engine.start(EngineConfig.ByeDpi(socksPort = port))
            assertIs<StartResult.Success>(result)
            assertEquals(port, result.socksPort)
        } finally {
            server.close()
        }
    }

    @Test
    fun startFailureWhenSocksPortNeverOpens() = runTest {
        every { proxy.startProxy(any()) } returns -1
        val result = engine.start(EngineConfig.ByeDpi(socksPort = 19998))
        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun startArgsIncludePortFlag() = runTest {
        val server = ServerSocket(0)
        val port = server.localPort
        server.acceptSocks5InBackground()
        try {
            every { proxy.startProxy(any()) } answers {
                Thread.sleep(60_000)
                0
            }
            engine.start(EngineConfig.ByeDpi(socksPort = port))
            verify {
                proxy.startProxy(
                    match { args -> args.contains("-p") && args.contains(port.toString()) }
                )
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun stopCallsJniStopProxy() = runTest {
        val server = ServerSocket(0)
        val port = server.localPort
        server.acceptSocks5InBackground()
        try {
            every { proxy.startProxy(any()) } answers {
                Thread.sleep(60_000)
                0
            }
            engine.start(EngineConfig.ByeDpi(socksPort = port))
            engine.stop()
            verify { proxy.stopProxy() }
        } finally {
            server.close()
        }
    }

    @Test
    fun probeFailsWhenEngineNotStarted() = runTest {
        val result = engine.probe()
        assertIs<ProbeResult.Failure>(result)
    }

    @Test
    fun probeSuccessWhenSocketListens() = runTest {
        val server = ServerSocket(0)
        val port = server.localPort
        server.acceptSocks5InBackground(repeat = 2)
        try {
            every { proxy.startProxy(any()) } answers {
                Thread.sleep(60_000)
                0
            }
            engine.start(EngineConfig.ByeDpi(socksPort = port))
            val result = engine.probe()
            assertIs<ProbeResult.Success>(result)
        } finally {
            server.close()
        }
    }

    private fun ServerSocket.acceptSocks5InBackground(repeat: Int = 1) {
        soTimeout = 200
        thread(isDaemon = true) {
            var count = 0
            while (count < repeat && !isClosed) {
                runCatching {
                    accept().use { c ->
                        c.getInputStream().read(ByteArray(8))
                        c.getOutputStream().write(byteArrayOf(0x05, 0x00))
                        c.getOutputStream().flush()
                    }
                    count++
                }
            }
        }
    }

    @Test
    fun probeFailsWhenNoSocketListening() = runTest {
        every { proxy.startProxy(any()) } returns 0
        engine.start(EngineConfig.ByeDpi(socksPort = 19999))
        val result = engine.probe()
        assertIs<ProbeResult.Failure>(result)
    }

    @Test
    fun probeFailsAfterStartFailure() = runTest {
        every { proxy.startProxy(any()) } returns -1
        engine.start(EngineConfig.ByeDpi(socksPort = 12345))
        val result = engine.probe()
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
        val server = ServerSocket(0)
        val port = server.localPort
        server.acceptSocks5InBackground()
        try {
            every { proxy.startProxy(any()) } answers {
                Thread.sleep(60_000)
                0
            }
            engine.start(EngineConfig.ByeDpi(args = "", socksPort = port))
            verify {
                proxy.startProxy(
                    match { args -> args.none { it.isEmpty() } },
                )
            }
        } finally {
            server.close()
        }
    }
}
