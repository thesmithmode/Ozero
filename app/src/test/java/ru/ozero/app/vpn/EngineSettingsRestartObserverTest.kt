package ru.ozero.app.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.app.settings.SettingsModel
import ru.ozero.commonvpn.split.SplitTunnelMode
import ru.ozero.coreapi.EngineId
import ru.ozero.coreorchestrator.OrchestratorState
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EngineSettingsRestartObserverTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `triggers drops first emission`() = runTest {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())

        val collected = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val job = launch { observer.triggers.toList(collected) }

        flow.emit(SettingsModel.DEFAULT)
        advanceUntilIdle()

        assertTrue(collected.isEmpty(), "drop(1) must skip the initial emission")
        job.cancel()
    }

    @Test
    fun `triggers emits snapshot when watched fields change`() = runTest {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())

        val collected = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val job = launch { observer.triggers.toList(collected) }

        flow.emit(SettingsModel.DEFAULT)
        flow.emit(
            SettingsModel.DEFAULT.copy(
                manualEngine = EngineId.BYEDPI,
                ipv6Enabled = true,
                splitMode = SplitTunnelMode.ALLOWLIST,
                byedpiWinningArgs = "  --foo  ",
            ),
        )
        advanceUntilIdle()

        assertEquals(1, collected.size)
        val snap = collected.single()
        assertEquals(EngineId.BYEDPI, snap.manualEngine)
        assertEquals("--foo", snap.byedpiWinningArgs)
        assertEquals(SplitTunnelMode.ALLOWLIST, snap.splitMode)
        assertEquals(true, snap.ipv6Enabled)
        job.cancel()
    }

    @Test
    fun `triggers ignores changes outside the watched set`() = runTest {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())

        val collected = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val job = launch { observer.triggers.toList(collected) }

        flow.emit(SettingsModel.DEFAULT)
        flow.emit(SettingsModel.DEFAULT.copy(autoStart = true))
        flow.emit(SettingsModel.DEFAULT.copy(autoStart = true, urnetworkEnabled = true))
        advanceUntilIdle()

        assertTrue(collected.isEmpty(), "autoStart/urnetwork are not in the watched set")
        job.cancel()
    }

    @Test
    fun `handle invokes restart only when state is Connected`() = runTest {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val state = MutableStateFlow<OrchestratorState>(OrchestratorState.Idle)
        val restarts = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val observer = EngineSettingsRestartObserver(
            settingsFlow = flow,
            vpnStateProvider = { state.value },
            onRestartConnected = { restarts += it },
        )

        val snapshot = EngineSettingsRestartObserver.Snapshot(
            manualEngine = EngineId.BYEDPI,
            byedpiWinningArgs = null,
            splitMode = SplitTunnelMode.ALL,
            ipv6Enabled = false,
        )
        observer.handle(snapshot)
        assertTrue(restarts.isEmpty(), "no restart while Idle")

        state.value = OrchestratorState.Connected(EngineId.BYEDPI, 1080)
        observer.handle(snapshot)
        assertEquals(listOf(snapshot), restarts, "restart fires when Connected")
    }

    private fun newObserver(
        flow: MutableSharedFlow<SettingsModel>,
        stateProvider: () -> OrchestratorState,
    ): EngineSettingsRestartObserver = EngineSettingsRestartObserver(
        settingsFlow = flow,
        vpnStateProvider = stateProvider,
        onRestartConnected = {},
    )

    private fun alwaysConnected(): () -> OrchestratorState = {
        OrchestratorState.Connected(engineId = EngineId.BYEDPI, socksPort = 1080)
    }
}
