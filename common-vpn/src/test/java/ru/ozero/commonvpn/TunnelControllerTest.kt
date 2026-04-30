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
    fun fullHappyPath_idleProbingConnectingConnectedDisconnectingIdle() {
        controller.onProbing()
        assertIs<TunnelState.Probing>(controller.state.value)

        controller.onConnecting(EngineId.BYEDPI)
        val connecting = controller.state.value
        assertIs<TunnelState.Connecting>(connecting)
        assertEquals(EngineId.BYEDPI, connecting.engineId)

        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        val connected = controller.state.value
        assertIs<TunnelState.Connected>(connected)
        assertEquals(1080, connected.socksPort)

        controller.onDisconnecting()
        assertIs<TunnelState.Disconnecting>(controller.state.value)

        controller.reset()
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun engineDeathFromConnectedTransitionsToFailed() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onEngineDied(EngineId.BYEDPI, "process exited with code 1")
        val state = controller.state.value
        assertIs<TunnelState.Failed>(state)
        assertEquals(EngineId.BYEDPI, state.engineId)
        assertEquals("process exited with code 1", state.reason)
    }

    @Test
    fun failedStateIsNotIdleKillSwitchInvariant() {
        controller.onProbing()
        controller.onEngineDied(EngineId.BYEDPI, "crash")
        assertTrue(controller.state.value is TunnelState.Failed)
        assertTrue(controller.state.value !is TunnelState.Idle)
    }

    @Test
    fun failedCanRetryViaProbing() {
        controller.onProbing()
        controller.onEngineDied(EngineId.BYEDPI, "crash")
        assertIs<TunnelState.Failed>(controller.state.value)

        controller.onProbing()
        assertIs<TunnelState.Probing>(controller.state.value)
    }

    @Test
    fun invalidIdleToConnectedIgnored() {
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun invalidIdleToConnectingIgnored() {
        controller.onConnecting(EngineId.BYEDPI)
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun invalidIdleToDisconnectingIgnored() {
        controller.onDisconnecting()
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun invalidConnectedToConnectingIgnored() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onConnecting(EngineId.BYEDPI)
        val state = controller.state.value
        assertIs<TunnelState.Connected>(state)
        assertEquals(1080, state.socksPort)
    }

    @Test
    fun invalidConnectedToProbingIgnored() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onProbing()
        assertIs<TunnelState.Connected>(controller.state.value)
    }

    @Test
    fun invalidProbingToConnectedIgnored() {
        controller.onProbing()
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        assertIs<TunnelState.Probing>(controller.state.value)
    }

    @Test
    fun idempotentDuplicateConnecting() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onConnecting(EngineId.BYEDPI)
        val state = controller.state.value
        assertIs<TunnelState.Connecting>(state)
        assertEquals(EngineId.BYEDPI, state.engineId)
    }

    @Test
    fun idempotentDuplicateDisconnecting() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onDisconnecting()
        controller.onDisconnecting()
        assertIs<TunnelState.Disconnecting>(controller.state.value)
    }

    @Test
    fun probingCanGoToFailedDirectly() {
        controller.onProbing()
        controller.onEngineDied(EngineId.BYEDPI, "no engines available")
        assertIs<TunnelState.Failed>(controller.state.value)
    }

    @Test
    fun connectingCanGoToFailedDirectly() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineDied(EngineId.BYEDPI, "engine start timeout")
        assertIs<TunnelState.Failed>(controller.state.value)
    }

    @Test
    fun disconnectingCanResetToIdle() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onDisconnecting()
        controller.reset()
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun invalidConnectedToIdleIgnored() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.reset()
        assertIs<TunnelState.Connected>(controller.state.value)
    }

    @Test
    fun failedCanGoToIdleViaReset() {
        controller.onProbing()
        controller.onEngineDied(EngineId.BYEDPI, "crash")
        controller.reset()
        assertIs<TunnelState.Idle>(controller.state.value)
    }

    @Test
    fun connectedCanGoDirectToFailed() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onEngineStarted(EngineId.BYEDPI, 1080)
        controller.onEngineDied(EngineId.BYEDPI, "engine died after connected")
        assertIs<TunnelState.Failed>(controller.state.value)
    }

    @Test
    fun probingCanCancelToDisconnecting() {
        controller.onProbing()
        controller.onDisconnecting()
        assertIs<TunnelState.Disconnecting>(controller.state.value)
    }

    @Test
    fun connectingCanCancelToDisconnecting() {
        controller.onProbing()
        controller.onConnecting(EngineId.BYEDPI)
        controller.onDisconnecting()
        assertIs<TunnelState.Disconnecting>(controller.state.value)
    }
}
