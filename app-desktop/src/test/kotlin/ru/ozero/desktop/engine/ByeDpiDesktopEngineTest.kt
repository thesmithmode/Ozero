package ru.ozero.desktop.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ByeDpiDesktopEngineTest {

    private val engine = ByeDpiDesktopEngine()

    @Test
    fun `should have BYEDPI id`() {
        assertEquals(ru.ozero.desktop.model.EngineId.BYEDPI, engine.id)
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
            assertEquals(5555, engine.extractPort(EngineConfig(socksPort = 5555)))
        }

        @Test
        fun `should return default 1080 when not set`() {
            assertEquals(ByeDpiDesktopEngine.DEFAULT_SOCKS_PORT, engine.extractPort(EngineConfig()))
        }
    }

    @Nested
    inner class BuildCommand {

        @Test
        fun `should include binary path`() {
            val cmd = engine.buildCommand(EngineConfig(), "/usr/bin/ciadpi")
            assertEquals("/usr/bin/ciadpi", cmd[0])
        }

        @Test
        fun `should include ip and port flags`() {
            val cmd = engine.buildCommand(EngineConfig(socksPort = 2222), "byedpi")
            assertTrue(cmd.contains("--ip"))
            assertTrue(cmd.contains("127.0.0.1"))
            assertTrue(cmd.contains("--port"))
            assertTrue(cmd.contains("2222"))
        }

        @Test
        fun `should use default port when not specified`() {
            val cmd = engine.buildCommand(EngineConfig(), "byedpi")
            assertTrue(cmd.contains("1080"))
        }

        @Test
        fun `should append extra args`() {
            val config = EngineConfig(extraArgs = listOf("--split", "2", "--disorder", "1"))
            val cmd = engine.buildCommand(config, "byedpi")
            assertTrue(cmd.contains("--split"))
            assertTrue(cmd.contains("2"))
            assertTrue(cmd.contains("--disorder"))
            assertTrue(cmd.contains("1"))
        }

        @Test
        fun `should not include extra args when empty`() {
            val cmd = engine.buildCommand(EngineConfig(), "byedpi")
            assertEquals(5, cmd.size)
        }
    }

    @Nested
    inner class DetectReady {

        @Test
        fun `should detect listen keyword`() {
            assertTrue(engine.detectReady("listen on 127.0.0.1:1080"))
        }

        @Test
        fun `should detect started keyword`() {
            assertTrue(engine.detectReady("byedpi started"))
        }

        @Test
        fun `should not detect unrelated output`() {
            assertFalse(engine.detectReady("parsing config"))
        }
    }
}
