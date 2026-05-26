package ru.ozero.desktop.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TunnelStateTest {

    @Nested
    inner class Idle {
        @Test
        fun `should be singleton`() {
            assertTrue(TunnelState.Idle === TunnelState.Idle)
        }
    }

    @Nested
    inner class Connecting {
        @Test
        fun `should preserve engineId`() {
            val state = TunnelState.Connecting(EngineId.BYEDPI)
            assertEquals(EngineId.BYEDPI, state.engineId)
        }
    }

    @Nested
    inner class Connected {
        @Test
        fun `should preserve engineId and port`() {
            val state = TunnelState.Connected(EngineId.SINGBOX, 7890)
            assertEquals(EngineId.SINGBOX, state.engineId)
            assertEquals(7890, state.socksPort)
        }
    }

    @Nested
    inner class Failed {
        @Test
        fun `should preserve engineId and reason`() {
            val state = TunnelState.Failed(EngineId.WARP, "binary missing")
            assertEquals(EngineId.WARP, state.engineId)
            assertEquals("binary missing", state.reason)
        }
    }

    @Nested
    inner class Disconnecting {
        @Test
        fun `should be singleton`() {
            assertTrue(TunnelState.Disconnecting === TunnelState.Disconnecting)
        }
    }

    @Nested
    inner class Probing {
        @Test
        fun `should have null engineId by default`() {
            assertNull(TunnelState.Probing().engineId)
        }

        @Test
        fun `should preserve engineId when set`() {
            val state = TunnelState.Probing(EngineId.SINGBOX)
            assertEquals(EngineId.SINGBOX, state.engineId)
        }
    }
}

class SwitchingTransitionTest {

    @Test
    fun `should preserve from and to`() {
        val transition = SwitchingTransition(EngineId.BYEDPI, EngineId.WARP)
        assertEquals(EngineId.BYEDPI, transition.from)
        assertEquals(EngineId.WARP, transition.to)
    }

    @Test
    fun `should allow null from`() {
        val transition = SwitchingTransition(null, EngineId.SINGBOX)
        assertNull(transition.from)
    }
}
