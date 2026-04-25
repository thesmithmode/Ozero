package ru.ozero.enginehysteria2

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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Hy2EngineTest {
    private lateinit var delegate: LibHy2Delegate
    private lateinit var engine: Hy2Engine

    @BeforeEach
    fun setUp() {
        delegate = mockk(relaxed = true)
        engine = Hy2Engine(delegate)
    }

    @Test
    fun engineIdIsHysteria2() {
        assertEquals(EngineId.HYSTERIA2, engine.id)
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
    fun startRequiresHysteria2Config() = runTest {
        val ex = runCatching { engine.start(EngineConfig.ByeDpi()) }.exceptionOrNull()
        assertIs<IllegalArgumentException>(ex)
    }

    @Test
    fun startFailsOnBlankConfigJson() = runTest {
        val result = engine.start(EngineConfig.Hysteria2(configJson = "   ", socksPort = 10809))
        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun startSuccessWhenDelegateReturnsZero() = runTest {
        every { delegate.startHy2(any()) } returns 0
        val result = engine.start(EngineConfig.Hysteria2(configJson = """{"x":1}""", socksPort = 10809))
        assertIs<StartResult.Success>(result)
        assertEquals(10809, result.socksPort)
    }

    @Test
    fun startFailureWhenDelegateReturnsNonZero() = runTest {
        every { delegate.startHy2(any()) } returns -7
        val result = engine.start(EngineConfig.Hysteria2(configJson = """{"x":1}"""))
        assertIs<StartResult.Failure>(result)
        assertTrue(result.reason.contains("-7"))
    }

    @Test
    fun startPassesConfigJsonToDelegate() = runTest {
        every { delegate.startHy2(any()) } returns 0
        val json = """{"server":"h:443"}"""
        engine.start(EngineConfig.Hysteria2(configJson = json, socksPort = 10809))
        verify { delegate.startHy2(json) }
    }

    @Test
    fun stopCallsDelegateStop() = runTest {
        every { delegate.startHy2(any()) } returns 0
        engine.start(EngineConfig.Hysteria2(configJson = """{"x":1}"""))
        engine.stop()
        verify { delegate.stopHy2() }
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
        try {
            every { delegate.startHy2(any()) } returns 0
            engine.start(EngineConfig.Hysteria2(configJson = """{"x":1}""", socksPort = port))
            val result = engine.probe()
            assertIs<ProbeResult.Success>(result)
        } finally {
            server.close()
        }
    }

    @Test
    fun probeFailsAfterStartFailure() = runTest {
        every { delegate.startHy2(any()) } returns -1
        engine.start(EngineConfig.Hysteria2(configJson = """{"x":1}""", socksPort = 12345))
        val result = engine.probe()
        assertIs<ProbeResult.Failure>(result)
    }

    @Test
    fun stopDeactivatesPortSoProbeFails() = runTest {
        val server = ServerSocket(0)
        val port = server.localPort
        try {
            every { delegate.startHy2(any()) } returns 0
            engine.start(EngineConfig.Hysteria2(configJson = """{"x":1}""", socksPort = port))
            engine.stop()
            val result = engine.probe()
            assertIs<ProbeResult.Failure>(result)
        } finally {
            server.close()
        }
    }

    @Test
    fun startExceptionFromDelegateWrappedInFailure() = runTest {
        every { delegate.startHy2(any()) } throws RuntimeException("native crash")
        val result = engine.start(EngineConfig.Hysteria2(configJson = """{"x":1}"""))
        assertIs<StartResult.Failure>(result)
        assertTrue(result.reason.contains("native crash"))
    }
}
