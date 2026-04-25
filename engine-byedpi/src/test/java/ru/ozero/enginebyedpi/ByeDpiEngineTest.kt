package ru.ozero.enginebyedpi

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId
import ru.ozero.coreapi.ProbeResult
import ru.ozero.coreapi.StartResult
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ByeDpiEngineTest {
    private lateinit var proxy: ByeDpiProxy
    private lateinit var engine: ByeDpiEngine

    @BeforeEach
    fun setUp() {
        proxy = mockk(relaxed = true)
        engine = ByeDpiEngine(proxy)
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
    fun startSuccessWhenJniReturnsZero() = runTest {
        every { proxy.jniStartProxy(any()) } returns 0
        val result = engine.start(EngineConfig.ByeDpi(socksPort = 1080))
        assertIs<StartResult.Success>(result)
        assertEquals(1080, result.socksPort)
    }

    @Test
    fun startFailureWhenJniReturnsNonZero() = runTest {
        every { proxy.jniStartProxy(any()) } returns -1
        val result = engine.start(EngineConfig.ByeDpi())
        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun startArgsIncludePortFlag() = runTest {
        every { proxy.jniStartProxy(any()) } returns 0
        engine.start(EngineConfig.ByeDpi(socksPort = 2080))
        verify {
            proxy.jniStartProxy(
                match { args -> args.contains("-p") && args.contains("2080") }
            )
        }
    }

    @Test
    fun stopCallsJniStopProxy() = runTest {
        every { proxy.jniStartProxy(any()) } returns 0
        engine.start(EngineConfig.ByeDpi())
        engine.stop()
        verify { proxy.jniStopProxy() }
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
        server.acceptSocks5InBackground()
        try {
            every { proxy.jniStartProxy(any()) } returns 0
            engine.start(EngineConfig.ByeDpi(socksPort = port))
            val result = engine.probe()
            assertIs<ProbeResult.Success>(result)
        } finally {
            server.close()
        }
    }

    private fun ServerSocket.acceptSocks5InBackground() {
        thread(isDaemon = true) {
            runCatching {
                accept().use { c ->
                    c.getInputStream().read(ByteArray(8))
                    c.getOutputStream().write(byteArrayOf(0x05, 0x00))
                    c.getOutputStream().flush()
                }
            }
        }
    }

    @Test
    fun probeFailsWhenNoSocketListening() = runTest {
        every { proxy.jniStartProxy(any()) } returns 0
        engine.start(EngineConfig.ByeDpi(socksPort = 19999))
        val result = engine.probe()
        assertIs<ProbeResult.Failure>(result)
    }

    @Test
    fun probeFailsAfterStartFailure() = runTest {
        // Критично: если jniStartProxy упал, probe НЕ должен видеть "активный" порт.
        every { proxy.jniStartProxy(any()) } returns -1
        engine.start(EngineConfig.ByeDpi(socksPort = 12345))
        val result = engine.probe()
        assertIs<ProbeResult.Failure>(result)
        assertEquals("движок не запущен", result.reason)
    }

    @Test
    fun startWithBlankArgsDoesNotPassEmptyToken() = runTest {
        every { proxy.jniStartProxy(any()) } returns 0
        engine.start(EngineConfig.ByeDpi(args = "", socksPort = 1080))
        verify {
            proxy.jniStartProxy(
                match { args -> args.none { it.isEmpty() } },
            )
        }
    }
}
