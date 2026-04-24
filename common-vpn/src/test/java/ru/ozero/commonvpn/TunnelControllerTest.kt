package ru.ozero.commonvpn

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
        controller.onEngineStarted(socksPort = 1080)
        val state = controller.state.value
        assertIs<TunnelState.Connected>(state)
        assertEquals(1080, state.socksPort)
    }

    @Test
    fun engineDeathTransitionsToDead() {
        controller.onEngineStarted(1080)
        controller.onEngineDied("process exited with code 1")
        val state = controller.state.value
        assertIs<TunnelState.Dead>(state)
        assertEquals("process exited with code 1", state.reason)
    }

    @Test
    fun deadStateIsNotIdleKillSwitchInvariant() {
        // Kill-switch: Dead != Idle. При Dead TUN остаётся открытым, блокируя трафик.
        // При Idle TUN может быть закрыт. Эти состояния нельзя путать.
        controller.onEngineStarted(1080)
        controller.onEngineDied("crash")
        assertTrue(controller.state.value is TunnelState.Dead)
        assertTrue(controller.state.value !is TunnelState.Idle)
    }

    @Test
    fun reconnectFromDeadTransitionsToConnected() {
        controller.onEngineStarted(1080)
        controller.onEngineDied("crash")
        controller.onReconnecting()
        assertIs<TunnelState.Disconnecting>(controller.state.value)
        controller.onReconnected(socksPort = 1081)
        val state = controller.state.value
        assertIs<TunnelState.Connected>(state)
        assertEquals(1081, state.socksPort)
    }

    @Test
    fun resetReturnsToIdle() {
        controller.onEngineStarted(1080)
        controller.reset()
        assertIs<TunnelState.Idle>(controller.state.value)
    }
}
