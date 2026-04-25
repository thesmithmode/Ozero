package ru.ozero.enginenaive

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import kotlin.test.assertTrue

class NaiveEngineTest {
    private lateinit var delegate: LibNaiveDelegate
    private lateinit var engine: NaiveEngine

    @BeforeEach
    fun setUp() {
        delegate = mockk(relaxed = true)
        engine = NaiveEngine(delegate)
    }

    @Test fun engineIdIsNaive() = assertEquals(EngineId.NAIVE, engine.id)

    @Test
    fun startRequiresNaiveConfig() = runTest {
        val ex = runCatching { engine.start(EngineConfig.ByeDpi()) }.exceptionOrNull()
        assertIs<IllegalArgumentException>(ex)
    }

    @Test
    fun startSuccessWhenDelegateReturnsZero() = runTest {
        every { delegate.startNaive(any()) } returns 0
        val r = engine.start(EngineConfig.Naive(proxyUrl = "https://u:p@h:443", socksPort = 1080))
        assertIs<StartResult.Success>(r)
        assertEquals(1080, r.socksPort)
    }

    @Test
    fun startWrapsProxyUrlIntoJsonWhenPlain() = runTest {
        val cfg = slot<String>()
        every { delegate.startNaive(capture(cfg)) } returns 0
        engine.start(EngineConfig.Naive(proxyUrl = "https://u:p@h:443", socksPort = 1080))
        val captured = cfg.captured
        assertTrue(captured.contains("\"listen\":\"socks://127.0.0.1:1080\""))
        assertTrue(captured.contains("\"proxy\":\"https://u:p@h:443\""))
    }

    @Test
    fun startPassesPrebuiltJsonAsIs() = runTest {
        val cfg = slot<String>()
        every { delegate.startNaive(capture(cfg)) } returns 0
        val pre = """{"listen":"socks://127.0.0.1:9999","proxy":"https://u:p@h:443","log":""}"""
        engine.start(EngineConfig.Naive(proxyUrl = pre, socksPort = 9999))
        assertEquals(pre, cfg.captured)
    }

    @Test
    fun startFailureWhenDelegateNonZero() = runTest {
        every { delegate.startNaive(any()) } returns -2
        val r = engine.start(EngineConfig.Naive(proxyUrl = "https://u:p@h:443"))
        assertIs<StartResult.Failure>(r)
        assertTrue(r.reason.contains("-2"))
    }

    @Test
    fun stopCallsDelegate() = runTest {
        every { delegate.startNaive(any()) } returns 0
        engine.start(EngineConfig.Naive(proxyUrl = "https://u:p@h:443"))
        engine.stop()
        verify { delegate.stopNaive() }
    }

    @Test
    fun probeFailsWhenNotStarted() = runTest {
        assertIs<ProbeResult.Failure>(engine.probe())
    }

    @Test
    fun probeSuccessWhenSocketListens() = runTest {
        val server = ServerSocket(0)
        server.acceptSocks5InBackground()
        try {
            every { delegate.startNaive(any()) } returns 0
            engine.start(EngineConfig.Naive(proxyUrl = "https://u:p@h:443", socksPort = server.localPort))
            assertIs<ProbeResult.Success>(engine.probe())
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
        every { delegate.startNaive(any()) } returns -1
        engine.start(EngineConfig.Naive(proxyUrl = "https://u:p@h:443", socksPort = 12345))
        assertIs<ProbeResult.Failure>(engine.probe())
    }

    @Test
    fun startExceptionWrappedInFailure() = runTest {
        every { delegate.startNaive(any()) } throws RuntimeException("spawn error")
        val r = engine.start(EngineConfig.Naive(proxyUrl = "https://u:p@h:443"))
        assertIs<StartResult.Failure>(r)
        assertTrue(r.reason.contains("spawn error"))
    }
}
