package ru.ozero.desktop.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class WarpDesktopEngineTest {

    private val engine = WarpDesktopEngine()

    @Test
    fun `should have WARP id`() {
        assertEquals(ru.ozero.desktop.model.EngineId.WARP, engine.id)
    }

    @Test
    fun `should be available on platform`() {
        assertTrue(engine.isAvailableOnPlatform)
    }

    @Test
    fun `should not be running initially`() {
        assertFalse(engine.isRunning())
    }

    @Nested
    inner class ExtractPort {

        @Test
        fun `should return config port when set`() {
            assertEquals(3333, engine.extractPort(EngineConfig(socksPort = 3333)))
        }

        @Test
        fun `should return default 7891 when not set`() {
            assertEquals(WarpDesktopEngine.DEFAULT_SOCKS_PORT, engine.extractPort(EngineConfig()))
        }
    }

    @Nested
    inner class BuildCommand {

        @Test
        fun `should return empty command when no warp config`() {
            val cmd = engine.buildCommand(EngineConfig(), "amneziawg")
            assertTrue(cmd.isEmpty())
        }

        @Test
        fun `should use config file when warp config present`() {
            val config = EngineConfig(warpConfig = "[Interface]\nPrivateKey=xxx\n")
            val cmd = engine.buildCommand(config, "amneziawg")
            assertEquals("amneziawg", cmd[0])
            assertEquals("-f", cmd[1])
            assertTrue(cmd[2].endsWith(".conf"))
        }
    }

    @Nested
    inner class DetectReady {

        @Test
        fun `should detect UAPI listener`() {
            assertTrue(engine.detectReady("UAPI listener started"))
        }

        @Test
        fun `should detect device started`() {
            assertTrue(engine.detectReady("device started on wg0"))
        }

        @Test
        fun `should not detect unrelated output`() {
            assertFalse(engine.detectReady("loading keys..."))
        }
    }
}
