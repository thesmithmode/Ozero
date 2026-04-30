package ru.ozero.app.ui.diag

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.TunnelController
import ru.ozero.enginescore.EngineId
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var tunnelController: TunnelController
    private lateinit var engine: FakeDiagnosticsEngine
    private lateinit var viewModel: DiagnosticsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        tunnelController = TunnelController()
        engine = FakeDiagnosticsEngine()
        viewModel = DiagnosticsViewModel(tunnelController, engine)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is NotConnected when tunnel Idle`() {
        assertIs<DiagnosticsUiState.NotConnected>(viewModel.uiState.value)
    }

    @Test
    fun `state becomes Idle when tunnel transitions to Connected`() = runTest {
        connect(socksPort = 1080)
        advanceUntilIdle()

        assertIs<DiagnosticsUiState.Idle>(viewModel.uiState.value)
    }

    @Test
    fun `onRun without Connected state is no-op`() = runTest {
        viewModel.onRun()
        advanceUntilIdle()

        assertEquals(0, engine.invocations)
        assertIs<DiagnosticsUiState.NotConnected>(viewModel.uiState.value)
    }

    @Test
    fun `onRun while Connected switches to Running then Done`() = runTest {
        connect(socksPort = 1080)
        advanceUntilIdle()

        val expected = listOf(
            DiagResult.Success("https://a", 100, 200),
            DiagResult.Failure("https://b", "timeout"),
        )
        engine.deferred.complete(expected)

        viewModel.onRun()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<DiagnosticsUiState.Done>(state)
        assertEquals(expected, state.results)
        assertEquals(1080, engine.lastPort)
    }

    @Test
    fun `onRun keeps Running until engine returns`() = runTest {
        connect(socksPort = 9050)
        advanceUntilIdle()

        viewModel.onRun()
        advanceUntilIdle()

        val running = viewModel.uiState.value
        assertIs<DiagnosticsUiState.Running>(running)
        assertTrue(running.total > 0)
        assertEquals(0, running.completed)

        engine.deferred.complete(emptyList())
        advanceUntilIdle()

        assertIs<DiagnosticsUiState.Done>(viewModel.uiState.value)
    }

    @Test
    fun `onStop while Running cancels and returns to Idle`() = runTest {
        connect(socksPort = 1080)
        advanceUntilIdle()

        viewModel.onRun()
        advanceUntilIdle()
        assertIs<DiagnosticsUiState.Running>(viewModel.uiState.value)

        viewModel.onStop()
        advanceUntilIdle()

        assertIs<DiagnosticsUiState.Idle>(viewModel.uiState.value)
    }

    @Test
    fun `disconnect during Running falls back to NotConnected`() = runTest {
        connect(socksPort = 1080)
        advanceUntilIdle()

        viewModel.onRun()
        advanceUntilIdle()
        assertIs<DiagnosticsUiState.Running>(viewModel.uiState.value)

        tunnelController.onDisconnecting()
        tunnelController.reset()
        advanceUntilIdle()

        assertIs<DiagnosticsUiState.NotConnected>(viewModel.uiState.value)
    }

    @Test
    fun `onRun эмитит Running(completed=N) на каждый onTestDone callback`() = runTest {
        connect(socksPort = 1080)
        advanceUntilIdle()

        val progressEngine = ProgressEmittingEngine()
        val vm = DiagnosticsViewModel(tunnelController, progressEngine)

        vm.onRun()
        advanceUntilIdle()
        assertIs<DiagnosticsUiState.Running>(vm.uiState.value)

        progressEngine.emitOne(DiagResult.Success("https://a", 100, 200))
        advanceUntilIdle()
        val s1 = vm.uiState.value
        assertIs<DiagnosticsUiState.Running>(s1)
        assertEquals(1, s1.completed, "после первого onTestDone completed=1")

        progressEngine.emitOne(DiagResult.Success("https://b", 110, 200))
        advanceUntilIdle()
        val s2 = vm.uiState.value
        assertIs<DiagnosticsUiState.Running>(s2)
        assertEquals(2, s2.completed, "после второго onTestDone completed=2")

        progressEngine.complete()
        advanceUntilIdle()
        assertIs<DiagnosticsUiState.Done>(vm.uiState.value)
    }

    private fun connect(socksPort: Int) {
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, socksPort)
    }

    private class FakeDiagnosticsEngine : DiagnosticsEngine {
        var invocations = 0
        var lastPort = -1
        val deferred = CompletableDeferred<List<DiagResult>>()

        override suspend fun runAll(socksPort: Int, onTestDone: (DiagResult) -> Unit): List<DiagResult> {
            invocations++
            lastPort = socksPort
            return deferred.await()
        }
    }

    private class ProgressEmittingEngine : DiagnosticsEngine {
        private val results = mutableListOf<DiagResult>()
        private var callback: ((DiagResult) -> Unit)? = null
        private val done = CompletableDeferred<List<DiagResult>>()

        override suspend fun runAll(socksPort: Int, onTestDone: (DiagResult) -> Unit): List<DiagResult> {
            callback = onTestDone
            return done.await()
        }

        fun emitOne(r: DiagResult) {
            results += r
            callback?.invoke(r)
        }

        fun complete() {
            done.complete(results.toList())
        }
    }
}
