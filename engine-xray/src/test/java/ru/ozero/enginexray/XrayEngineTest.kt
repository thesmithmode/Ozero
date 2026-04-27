package ru.ozero.enginexray

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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class XrayEngineTest {
    private lateinit var delegate: LibXrayDelegate
    private lateinit var engine: XrayEngine

    @BeforeEach
    fun setUp() {
        delegate = mockk(relaxed = true)
        engine = XrayEngine(delegate)
    }

    @Test
    fun engineIdIsXray() {
        assertEquals(EngineId.XRAY, engine.id)
    }

    @Test
    fun capabilitiesFullStack() {
        val caps = engine.capabilities
        assertTrue(caps.supportsTcp)
        assertTrue(caps.supportsUdp)
        assertTrue(caps.supportsDoH)
        assertFalse(caps.localOnly)
        assertTrue(caps.requiresServer)
    }

    @Test
    fun startRequiresXrayConfig() = runTest {
        val ex = runCatching { engine.start(EngineConfig.ByeDpi()) }.exceptionOrNull()
        assertIs<IllegalArgumentException>(ex)
    }

    @Test
    fun startFailsOnBlankConfigJson() = runTest {
        val result = engine.start(EngineConfig.Xray(configJson = "   ", socksPort = 10808))
        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun startSuccessWhenDelegateReturnsZero() = runTest {
        every { delegate.startXray(any()) } returns 0
        val result = engine.start(EngineConfig.Xray(configJson = """{"x":1}""", socksPort = 10808))
        assertIs<StartResult.Success>(result)
        assertEquals(10808, result.socksPort)
    }

    @Test
    fun startFailureWhenDelegateReturnsNonZero() = runTest {
        every { delegate.startXray(any()) } returns -7
        val result = engine.start(EngineConfig.Xray(configJson = """{"x":1}"""))
        assertIs<StartResult.Failure>(result)
        assertTrue(result.reason.contains("-7"))
    }

    @Test
    fun startPassesConfigJsonToDelegate() = runTest {
        every { delegate.startXray(any()) } returns 0
        val json = """{"outbounds":[{"protocol":"vless"}]}"""
        engine.start(EngineConfig.Xray(configJson = json, socksPort = 10808))
        verify { delegate.startXray(json) }
    }

    @Test
    fun stopCallsDelegateStop() = runTest {
        every { delegate.startXray(any()) } returns 0
        engine.start(EngineConfig.Xray(configJson = """{"x":1}"""))
        engine.stop()
        verify { delegate.stopXray() }
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
            every { delegate.startXray(any()) } returns 0
            engine.start(EngineConfig.Xray(configJson = """{"x":1}""", socksPort = port))
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
    fun probeFailsAfterStartFailure() = runTest {
        every { delegate.startXray(any()) } returns -1
        engine.start(EngineConfig.Xray(configJson = """{"x":1}""", socksPort = 12345))
        val result = engine.probe()
        assertIs<ProbeResult.Failure>(result)
    }

    @Test
    fun stopDeactivatesPortSoProbeFails() = runTest {
        val server = ServerSocket(0)
        val port = server.localPort
        try {
            every { delegate.startXray(any()) } returns 0
            engine.start(EngineConfig.Xray(configJson = """{"x":1}""", socksPort = port))
            engine.stop()
            val result = engine.probe()
            assertIs<ProbeResult.Failure>(result)
        } finally {
            server.close()
        }
    }

    @Test
    fun startExceptionFromDelegateWrappedInFailure() = runTest {
        every { delegate.startXray(any()) } throws RuntimeException("native crash")
        val result = engine.start(EngineConfig.Xray(configJson = """{"x":1}"""))
        assertIs<StartResult.Failure>(result)
        assertTrue(result.reason.contains("native crash"))
    }
}
