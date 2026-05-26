package ru.ozero.desktop.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EngineConfigTest {

    @Nested
    inner class Defaults {
        @Test
        fun `should have zero socks port by default`() {
            assertEquals(0, EngineConfig().socksPort)
        }

        @Test
        fun `should have zero http port by default`() {
            assertEquals(0, EngineConfig().httpPort)
        }

        @Test
        fun `should have empty extra args by default`() {
            assertTrue(EngineConfig().extraArgs.isEmpty())
        }

        @Test
        fun `should have null singbox json by default`() {
            assertNull(EngineConfig().singboxJson)
        }

        @Test
        fun `should have null warp config by default`() {
            assertNull(EngineConfig().warpConfig)
        }
    }

    @Nested
    inner class CustomValues {
        @Test
        fun `should preserve socks port`() {
            assertEquals(1234, EngineConfig(socksPort = 1234).socksPort)
        }

        @Test
        fun `should preserve extra args`() {
            val args = listOf("--split", "2")
            assertEquals(args, EngineConfig(extraArgs = args).extraArgs)
        }

        @Test
        fun `should preserve singbox json`() {
            assertEquals("{}", EngineConfig(singboxJson = "{}").singboxJson)
        }
    }
}

class EngineStartResultTest {

    @Test
    fun `Success should preserve port`() {
        val result = EngineStartResult.Success(8080)
        assertEquals(8080, result.port)
    }

    @Test
    fun `BinaryMissing should preserve name`() {
        val result = EngineStartResult.BinaryMissing("sing-box.exe")
        assertEquals("sing-box.exe", result.binaryName)
    }

    @Test
    fun `PlatformUnavailable should preserve reason`() {
        val result = EngineStartResult.PlatformUnavailable("no go binding")
        assertEquals("no go binding", result.reason)
    }

    @Test
    fun `Failed should preserve reason and cause`() {
        val ex = RuntimeException("boom")
        val result = EngineStartResult.Failed("crash", ex)
        assertEquals("crash", result.reason)
        assertEquals(ex, result.cause)
    }

    @Test
    fun `Failed should allow null cause`() {
        val result = EngineStartResult.Failed("timeout")
        assertNull(result.cause)
    }
}
