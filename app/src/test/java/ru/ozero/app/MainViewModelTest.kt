package ru.ozero.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.app.ui.MainViewModel
import ru.ozero.coreorchestrator.Orchestrator
import ru.ozero.coreorchestrator.OrchestratorState
import ru.ozero.coreorchestrator.OrchestratorTransition
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var orchestrator: Orchestrator
    private lateinit var viewModel: MainViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        orchestrator = Orchestrator()
        viewModel = MainViewModel(orchestrator)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsIdle() {
        assertIs<OrchestratorState.Idle>(viewModel.state.value)
    }

    @Test
    fun onConnectClickFromIdleTransitionsToProbing() {
        viewModel.onConnectClick()
        assertIs<OrchestratorState.Probing>(viewModel.state.value)
    }

    @Test
    fun onConnectClickFromConnectedTransitionsToDisconnecting() {
        orchestrator.dispatch(OrchestratorTransition.Connect)
        orchestrator.dispatch(OrchestratorTransition.ProbeComplete(ru.ozero.coreapi.EngineId.BYEDPI))
        orchestrator.dispatch(OrchestratorTransition.ConnectSuccess(ru.ozero.coreapi.EngineId.BYEDPI, 1080))
        viewModel.onConnectClick()
        assertIs<OrchestratorState.Disconnecting>(viewModel.state.value)
    }

    @Test
    fun onVpnPermissionDeniedFromProbingTransitionsToFailed() {
        orchestrator.dispatch(OrchestratorTransition.Connect)
        viewModel.onVpnPermissionDenied()
        assertIs<OrchestratorState.Failed>(viewModel.state.value)
    }

    @Test
    fun onVpnPermissionDeniedFromNonProbingIsNoOp() {
        viewModel.onVpnPermissionDenied()
        assertIs<OrchestratorState.Idle>(viewModel.state.value)
    }
}
