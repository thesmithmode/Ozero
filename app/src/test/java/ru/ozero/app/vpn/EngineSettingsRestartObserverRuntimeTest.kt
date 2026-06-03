package ru.ozero.app.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.TrafficMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EngineSettingsRestartObserverRuntimeTest {

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
    fun `handle restart — startup fallback target can change runtime`() = runTest(dispatcher) {
        val state = MutableStateFlow<TunnelState>(TunnelState.Probing(EngineId.FPTN))
        val restarts = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val observer = newObserver(state) { restarts += it }
        val previous = snapshot(
            manualEngine = EngineId.WARP,
            customDnsServers = listOf("1.1.1.1"),
        )
        val current = snapshot(
            manualEngine = null,
            ipv6Enabled = true,
            trafficMode = TrafficMode.PROXY,
            customDnsServers = listOf("8.8.8.8"),
            engineAutoPriority = listOf(EngineId.FPTN),
        )

        observer.handle(trigger(previous = previous, snapshot = current))

        assertEquals(
            1,
            restarts.size,
            "runtime-changing startup snapshot that becomes the current target should not be silently accepted",
        )
    }

    @Test
    fun `handle skip — connected engine toggle away and back without runtime change`() = runTest(dispatcher) {
        val state = MutableStateFlow<TunnelState>(TunnelState.Connected(EngineId.BYEDPI, 1080))
        val restarts = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val observer = newObserver(state) { restarts += it }
        val previous = snapshot(
            manualEngine = EngineId.WARP,
            byedpiWinningArgs = "--same",
            customDnsServers = listOf("1.1.1.1"),
        )
        val current = previous.copy(manualEngine = EngineId.BYEDPI)

        observer.handle(trigger(previous = previous, snapshot = current))

        assertTrue(
            restarts.isEmpty(),
            "A debounced BYEDPI->WARP->BYEDPI toggle with unchanged BYEDPI runtime config is a no-op.",
        )
    }

    @Test
    fun `handle skip — connected same engine baseline to same engine without runtime change`() = runTest(dispatcher) {
        val state = MutableStateFlow<TunnelState>(TunnelState.Connected(EngineId.BYEDPI, 1080))
        val restarts = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val observer = newObserver(state) { restarts += it }
        val previous = snapshot(
            manualEngine = EngineId.BYEDPI,
            byedpiWinningArgs = "--same",
            customDnsServers = listOf("1.1.1.1"),
        )

        observer.handle(trigger(previous = previous, snapshot = previous))

        assertTrue(
            restarts.isEmpty(),
            "Real debounced BYEDPI->WARP->BYEDPI no-op emits baseline BYEDPI -> final BYEDPI and must not restart.",
        )
    }

    @Test
    fun `handle restart — BYEDPI toggle back with changed shared runtime fields`() = runTest(dispatcher) {
        val state = MutableStateFlow<TunnelState>(TunnelState.Connected(EngineId.BYEDPI, 1080))
        val restarts = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val observer = newObserver(state) { restarts += it }
        val previous = snapshot(
            manualEngine = EngineId.WARP,
            byedpiWinningArgs = "--same",
            customDnsServers = listOf("1.1.1.1"),
        )
        val current = previous.copy(
            manualEngine = EngineId.BYEDPI,
            ipv6Enabled = true,
            trafficMode = TrafficMode.PROXY,
            customDnsServers = listOf("8.8.8.8"),
        )

        observer.handle(trigger(previous = previous, snapshot = current))

        assertEquals(
            listOf(current),
            restarts,
            "Debounced BYEDPI->WARP->BYEDPI must still restart when startup-only shared runtime fields changed.",
        )
    }

    @Test
    fun `triggers preserve pre-burst baseline for debounced toggle back`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())
        val collected = mutableListOf<EngineSettingsRestartObserver.Trigger>()
        val job = collectTriggers(observer, collected)
        advanceUntilIdle()

        val baseline = SettingsModel.DEFAULT.copy(
            manualEngine = EngineId.BYEDPI,
            customDnsServers = listOf("1.1.1.1"),
        )
        val changed = baseline.copy(customDnsServers = listOf("8.8.8.8"))
        flow.emit(baseline)
        advanceRestartDebounce()
        advanceUntilIdle()
        flow.emit(changed.copy(manualEngine = EngineId.WARP))
        flow.emit(changed.copy(manualEngine = EngineId.BYEDPI))
        advanceRestartDebounce()
        advanceUntilIdle()

        assertEquals(1, collected.size)
        assertEquals(listOf("1.1.1.1"), collected.single().previous.customDnsServers)
        assertEquals(listOf("8.8.8.8"), collected.single().snapshot.customDnsServers)
        job.cancel()
    }

    @Test
    fun `triggers drops debounced no-op toggle back to baseline`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())
        val collected = mutableListOf<EngineSettingsRestartObserver.Trigger>()
        val job = collectTriggers(observer, collected)
        advanceUntilIdle()

        val baseline = SettingsModel.DEFAULT.copy(manualEngine = EngineId.BYEDPI)
        flow.emit(baseline)
        advanceRestartDebounce()
        advanceUntilIdle()
        flow.emit(baseline.copy(manualEngine = EngineId.WARP))
        flow.emit(baseline.copy(manualEngine = EngineId.BYEDPI))
        advanceRestartDebounce()
        advanceUntilIdle()

        assertTrue(
            collected.isEmpty(),
            "A debounced BYEDPI->WARP->BYEDPI burst returns to baseline and must not emit a restart trigger.",
        )
        job.cancel()
    }

    private fun newObserver(
        state: MutableStateFlow<TunnelState>,
        onRestartConnected: (EngineSettingsRestartObserver.Snapshot) -> Unit,
    ): EngineSettingsRestartObserver = EngineSettingsRestartObserver(
        settingsFlow = MutableSharedFlow(replay = 0, extraBufferCapacity = 8),
        vpnStateProvider = { state.value },
        onRestartConnected = onRestartConnected,
    )

    private fun newObserver(
        flow: MutableSharedFlow<SettingsModel>,
        stateProvider: () -> TunnelState,
    ): EngineSettingsRestartObserver = EngineSettingsRestartObserver(
        settingsFlow = flow,
        vpnStateProvider = stateProvider,
        onRestartConnected = {},
    )

    private fun snapshot(
        manualEngine: EngineId?,
        byedpiWinningArgs: String? = null,
        ipv6Enabled: Boolean = false,
        trafficMode: TrafficMode = TrafficMode.TUN,
        customDnsServers: List<String> = emptyList(),
        engineAutoPriority: List<EngineId>? = null,
    ): EngineSettingsRestartObserver.Snapshot = EngineSettingsRestartObserver.Snapshot(
        manualEngine = manualEngine,
        byedpiWinningArgs = byedpiWinningArgs,
        ipv6Enabled = ipv6Enabled,
        trafficMode = trafficMode,
        customDnsServers = customDnsServers,
        engineAutoPriority = engineAutoPriority,
    )

    private fun trigger(
        previous: EngineSettingsRestartObserver.Snapshot,
        snapshot: EngineSettingsRestartObserver.Snapshot,
    ) = EngineSettingsRestartObserver.Trigger(previous = previous, snapshot = snapshot)

    private fun alwaysConnected(): () -> TunnelState = {
        TunnelState.Connected(engineId = EngineId.BYEDPI, socksPort = 1080)
    }

    private fun TestScope.advanceRestartDebounce() {
        runCurrent()
        advanceTimeBy(EngineSettingsRestartObserver.RESTART_DEBOUNCE_MS_FOR_TESTS)
        runCurrent()
    }

    private fun TestScope.collectTriggers(
        observer: EngineSettingsRestartObserver,
        collected: MutableList<EngineSettingsRestartObserver.Trigger>,
    ): Job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        observer.triggers.toList(collected)
    }
}
