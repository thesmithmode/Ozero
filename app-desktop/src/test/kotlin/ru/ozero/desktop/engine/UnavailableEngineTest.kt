package ru.ozero.desktop.engine

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import ru.ozero.desktop.model.EngineId

class UnavailableEngineTest {

    private val engine = UnavailableEngine(
        id = EngineId.MASTERDNS,
        binaryName = "mdnsvpn",
        reason = "server-side only",
    )

    @Test
    fun `should preserve id`() {
        assertEquals(EngineId.MASTERDNS, engine.id)
    }

    @Test
    fun `should preserve binary name`() {
        assertEquals("mdnsvpn", engine.binaryName)
    }

    @Test
    fun `should not be available on platform`() {
        assertFalse(engine.isAvailableOnPlatform)
    }

    @Test
    fun `should not be running`() {
        assertFalse(engine.isRunning())
    }

    @Test
    fun `should return zero port`() {
        assertEquals(0, engine.listeningPort())
    }

    @Test
    fun `should return PlatformUnavailable on start`() = runTest {
        val result = engine.start(EngineConfig())
        assertTrue(result is EngineStartResult.PlatformUnavailable)
        assertEquals("server-side only", (result as EngineStartResult.PlatformUnavailable).reason)
    }

    @Test
    fun `stop should not throw`() = runTest {
        engine.stop()
    }
}

class DesktopEngineRegistryTest {

    @Test
    fun `should contain all platform engines`() {
        val engines = DesktopEngineRegistry.all()
        assertTrue(engines.containsKey(EngineId.SINGBOX))
        assertTrue(engines.containsKey(EngineId.BYEDPI))
        assertTrue(engines.containsKey(EngineId.WARP))
        assertTrue(engines.containsKey(EngineId.MASTERDNS))
        assertTrue(engines.containsKey(EngineId.URNETWORK))
        assertTrue(engines.containsKey(EngineId.FPTN))
    }

    @Test
    fun `should return null for unknown engine`() {
        assertNull(DesktopEngineRegistry.get(EngineId.XRAY))
    }

    @Test
    fun `should return engine for known id`() {
        assertNotNull(DesktopEngineRegistry.get(EngineId.SINGBOX))
    }

    @Nested
    inner class AvailableEngines {

        @Test
        fun `should include singbox byedpi warp`() {
            val available = DesktopEngineRegistry.available()
            val ids = available.map { it.id }
            assertTrue(EngineId.SINGBOX in ids)
            assertTrue(EngineId.BYEDPI in ids)
            assertTrue(EngineId.WARP in ids)
        }

        @Test
        fun `should exclude unavailable engines`() {
            val available = DesktopEngineRegistry.available()
            val ids = available.map { it.id }
            assertFalse(EngineId.MASTERDNS in ids)
            assertFalse(EngineId.URNETWORK in ids)
            assertFalse(EngineId.FPTN in ids)
        }
    }
}
