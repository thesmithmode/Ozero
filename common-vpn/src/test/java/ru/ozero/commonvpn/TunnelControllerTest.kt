package ru.ozero.commonvpn

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TunnelControllerTest {
    private lateinit var controller: TunnelController

    @BeforeEach
    fun setUp() {
        controller = TunnelController()
    }

    @Test
    fun initialStateIsIdle() {
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun engineStartedTransitionsToConnected() {
        controller.onEngineStarted(engineId = EngineId.BYEDPI, socksPort = 1080)
        val state = controller.state.value
        assertIs<TunnelState.Connected>(state)
        assertEquals(EngineId.BYEDPI, state.engineId)
        assertEquals(1080, state.socksPort)
    }

    @Test
    fun engineDeathTransitionsToFailed() {
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onEngineDied(EngineId.BYEDPI, "process exited with code 1")
        val state = controller.state.value
        assertIs<TunnelState.Failed>(state)
        assertEquals(EngineId.BYEDPI, state.engineId)
        assertEquals("process exited with code 1", state.reason)
    }

    @Test
    fun failedStateIsNotIdleKillSwitchInvariant() {
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onEngineDied(EngineId.BYEDPI, "crash")
        assertTrue(controller.state.value is TunnelState.Failed)
        assertTrue(controller.state.value !is TunnelState.Idle)
    }

    @Test
    fun probingThenConnectingThenConnected() {
        controller.onProbing()
        assertIs<TunnelState.Probing>(controller.state.value)
        controller.onConnecting(EngineId.BYEDPI)
        val connecting = controller.state.value
        assertIs<TunnelState.Connecting>(connecting)
        assertEquals(EngineId.BYEDPI, connecting.engineId)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        assertIs<TunnelState.Connected>(controller.state.value)
    }

    @Test
    fun disconnectingTransition() {
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onDisconnecting()
        assertIs<TunnelState.Disconnecting>(controller.state.value)
    }

    @Test
    fun resetReturnsToIdle() {
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.reset()
        assertIs<TunnelState.Idle>(controller.state.value)
    }
}
