package ru.ozero.engineamnezia

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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AwgEngineTest {
    private lateinit var delegate: LibAwgDelegate
    private lateinit var engine: AwgEngine

    @BeforeEach
    fun setUp() {
        delegate = mockk(relaxed = true)
        engine = AwgEngine(delegate)
    }

    @Test
    fun engineIdIsAmnezia() {
        assertEquals(EngineId.AMNEZIA, engine.id)
    }

    @Test
    fun startRequiresAmneziaConfig() = runTest {
        val ex = runCatching { engine.start(EngineConfig.ByeDpi()) }.exceptionOrNull()
        assertIs<IllegalArgumentException>(ex)
    }

    @Test
    fun startFailsOnBlankConfig() = runTest {
        val r = engine.start(EngineConfig.Amnezia(configJson = "  ", socksPort = 0))
        assertIs<StartResult.Failure>(r)
    }

    @Test
    fun startSuccessWhenDelegateReturnsZero() = runTest {
        every { delegate.startAwg(any()) } returns 0
        val r = engine.start(EngineConfig.Amnezia(configJson = "[Interface]\nPrivateKey=X", socksPort = 0))
        assertIs<StartResult.Success>(r)
    }

    @Test
    fun startFailureWhenDelegateNonZero() = runTest {
        every { delegate.startAwg(any()) } returns -3
        val r = engine.start(EngineConfig.Amnezia(configJson = "x"))
        assertIs<StartResult.Failure>(r)
        assertTrue(r.reason.contains("-3"))
    }

    @Test
    fun stopCallsDelegate() = runTest {
        every { delegate.startAwg(any()) } returns 0
        engine.start(EngineConfig.Amnezia(configJson = "x"))
        engine.stop()
        verify { delegate.stopAwg() }
    }

    @Test
    fun probeFailsWhenNotStarted() = runTest {
        val r = engine.probe()
        assertIs<ProbeResult.Failure>(r)
    }

    @Test
    fun probeSuccessWhenDelegateUp() = runTest {
        every { delegate.startAwg(any()) } returns 0
        every { delegate.isUp() } returns true
        engine.start(EngineConfig.Amnezia(configJson = "x"))
        val r = engine.probe()
        assertIs<ProbeResult.Success>(r)
    }

    @Test
    fun probeFailsWhenDelegateDown() = runTest {
        every { delegate.startAwg(any()) } returns 0
        every { delegate.isUp() } returns false
        engine.start(EngineConfig.Amnezia(configJson = "x"))
        val r = engine.probe()
        assertIs<ProbeResult.Failure>(r)
    }

    @Test
    fun probeFailsAfterStartFailure() = runTest {
        every { delegate.startAwg(any()) } returns -1
        engine.start(EngineConfig.Amnezia(configJson = "x"))
        val r = engine.probe()
        assertIs<ProbeResult.Failure>(r)
    }

    @Test
    fun startExceptionWrappedInFailure() = runTest {
        every { delegate.startAwg(any()) } throws RuntimeException("native crash")
        val r = engine.start(EngineConfig.Amnezia(configJson = "x"))
        assertIs<StartResult.Failure>(r)
        assertTrue(r.reason.contains("native crash"))
    }

    @Test
    fun stopDeactivatesProbe() = runTest {
        every { delegate.startAwg(any()) } returns 0
        every { delegate.isUp() } returns true
        engine.start(EngineConfig.Amnezia(configJson = "x"))
        engine.stop()
        val r = engine.probe()
        assertIs<ProbeResult.Failure>(r)
    }
}
