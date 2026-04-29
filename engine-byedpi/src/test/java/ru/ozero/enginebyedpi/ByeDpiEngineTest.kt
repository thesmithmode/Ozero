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
            every { proxy.jniStartProxy(any()) } answers {
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
        every { proxy.jniStartProxy(any()) } returns -1
        val result = engine.start(EngineConfig.ByeDpi(socksPort = 19998))
        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun startArgsIncludePortFlag() = runTest {
        val server = ServerSocket(0)
        val port = server.localPort
        server.acceptSocks5InBackground()
        try {
            every { proxy.jniStartProxy(any()) } answers {
                Thread.sleep(60_000)
                0
            }
            engine.start(EngineConfig.ByeDpi(socksPort = port))
            verify {
                proxy.jniStartProxy(
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
            every { proxy.jniStartProxy(any()) } answers {
                Thread.sleep(60_000)
                0
            }
            engine.start(EngineConfig.ByeDpi(socksPort = port))
            engine.stop()
            verify { proxy.jniStopProxy() }
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
            every { proxy.jniStartProxy(any()) } answers {
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
        thread(isDaemon = true) {
            runCatching {
                repeat(repeat) {
                    accept().use { c ->
                        c.getInputStream().read(ByteArray(8))
                        c.getOutputStream().write(byteArrayOf(0x05, 0x00))
                        c.getOutputStream().flush()
                    }
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
        every { proxy.jniStartProxy(any()) } returns -1
        engine.start(EngineConfig.ByeDpi(socksPort = 12345))
        val result = engine.probe()
        assertIs<ProbeResult.Failure>(result)
        assertEquals("движок не запущен", result.reason)
    }

    @Test
    fun startWithBlankArgsDoesNotPassEmptyToken() = runTest {
        val server = ServerSocket(0)
        val port = server.localPort
        server.acceptSocks5InBackground()
        try {
            every { proxy.jniStartProxy(any()) } answers {
                Thread.sleep(60_000)
                0
            }
            engine.start(EngineConfig.ByeDpi(args = "", socksPort = port))
            verify {
                proxy.jniStartProxy(
                    match { args -> args.none { it.isEmpty() } },
                )
            }
        } finally {
            server.close()
        }
    }
}
