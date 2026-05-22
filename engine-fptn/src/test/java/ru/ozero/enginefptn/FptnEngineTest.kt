package ru.ozero.enginefptn

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun `start with malformed base64 returns failure`() = runTest {
        every { Base64.decode(any<String>(), any<Int>()) } throws IllegalArgumentException("bad")
        val result = engine.start(EngineConfig.Fptn(token = "fptn:!!!"), Upstream.None)
        assertIs<StartResult.Failure>(result)
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

    private fun validTokenB64(): String {
        val json = """{"version":1,"username":"u","password":"p",
            "servers":[{"name":"S1","host":"1.2.3.4","port":443}]}"""
        return java.util.Base64.getEncoder().encodeToString(json.toByteArray())
    }
}
