package ru.ozero.coreorchestrator

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.coreapi.EngineId
import kotlin.test.assertIs
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrchestratorTest {
    private lateinit var orchestrator: Orchestrator

    @BeforeEach
    fun setUp() {
        orchestrator = Orchestrator()
    }

    @Test
    fun initialStateIsIdle() {
        assertIs<OrchestratorState.Idle>(orchestrator.state.value)
    }

    @Test
    fun idleConnectTransitionsToProbing() {
        orchestrator.dispatch(OrchestratorTransition.Connect)
        assertIs<OrchestratorState.Probing>(orchestrator.state.value)
    }

    @Test
    fun probingProbeCompleteTransitionsToConnecting() {
        orchestrator.dispatch(OrchestratorTransition.Connect)
        orchestrator.dispatch(OrchestratorTransition.ProbeComplete(EngineId.BYEDPI))
        val state = orchestrator.state.value
        assertIs<OrchestratorState.Connecting>(state)
        assertEquals(EngineId.BYEDPI, state.engineId)
    }

    @Test
    fun connectingSuccessTransitionsToConnected() {
        orchestrator.dispatch(OrchestratorTransition.Connect)
        orchestrator.dispatch(OrchestratorTransition.ProbeComplete(EngineId.BYEDPI))
        orchestrator.dispatch(OrchestratorTransition.ConnectSuccess(EngineId.BYEDPI, 1080))
        val state = orchestrator.state.value
        assertIs<OrchestratorState.Connected>(state)
        assertEquals(EngineId.BYEDPI, state.engineId)
        assertEquals(1080, state.socksPort)
    }

    @Test
    fun connectingFailedTransitionsToFailed() {
        orchestrator.dispatch(OrchestratorTransition.Connect)
        orchestrator.dispatch(OrchestratorTransition.ProbeComplete(EngineId.BYEDPI))
        orchestrator.dispatch(OrchestratorTransition.ConnectFailed(EngineId.BYEDPI, "timeout"))
        val state = orchestrator.state.value
        assertIs<OrchestratorState.Failed>(state)
        assertEquals("timeout", state.reason)
    }

    @Test
    fun connectedSwitchToTransitionsToSwitching() {
        connectTo(EngineId.BYEDPI)
        orchestrator.dispatch(OrchestratorTransition.SwitchTo(EngineId.XRAY))
        val state = orchestrator.state.value
        assertIs<OrchestratorState.Switching>(state)
        assertEquals(EngineId.BYEDPI, state.from)
        assertEquals(EngineId.XRAY, state.to)
    }

    @Test
    fun switchCompleteTransitionsToConnected() {
        connectTo(EngineId.BYEDPI)
        orchestrator.dispatch(OrchestratorTransition.SwitchTo(EngineId.XRAY))
        orchestrator.dispatch(OrchestratorTransition.SwitchComplete(EngineId.XRAY, 10808))
        val state = orchestrator.state.value
        assertIs<OrchestratorState.Connected>(state)
        assertEquals(EngineId.XRAY, state.engineId)
    }

    @Test
    fun connectedDisconnectTransitionsToDisconnecting() {
        connectTo(EngineId.BYEDPI)
        orchestrator.dispatch(OrchestratorTransition.Disconnect)
        assertIs<OrchestratorState.Disconnecting>(orchestrator.state.value)
    }

    @Test
    fun failedDisconnectTransitionsToDisconnecting() {
        orchestrator.dispatch(OrchestratorTransition.Connect)
        orchestrator.dispatch(OrchestratorTransition.ProbeComplete(EngineId.BYEDPI))
        orchestrator.dispatch(OrchestratorTransition.ConnectFailed(EngineId.BYEDPI, "err"))
        orchestrator.dispatch(OrchestratorTransition.Disconnect)
        assertIs<OrchestratorState.Disconnecting>(orchestrator.state.value)
    }

    @Test
    fun disconnectingCompleteTransitionsToIdle() {
        connectTo(EngineId.BYEDPI)
        orchestrator.dispatch(OrchestratorTransition.Disconnect)
        orchestrator.dispatch(OrchestratorTransition.DisconnectComplete)
        assertIs<OrchestratorState.Idle>(orchestrator.state.value)
    }

    @Test
    fun invalidTransitionThrowsIllegalState() {
        assertFailsWith<IllegalStateException> {
            orchestrator.dispatch(OrchestratorTransition.DisconnectComplete)
        }
    }

    @Test
    fun invalidTransitionFromConnectedToConnect() {
        connectTo(EngineId.BYEDPI)
        assertFailsWith<IllegalStateException> {
            orchestrator.dispatch(OrchestratorTransition.Connect)
        }
    }

    private fun connectTo(engineId: EngineId) {
        orchestrator.dispatch(OrchestratorTransition.Connect)
        orchestrator.dispatch(OrchestratorTransition.ProbeComplete(engineId))
        orchestrator.dispatch(OrchestratorTransition.ConnectSuccess(engineId, 1080))
    }
}
