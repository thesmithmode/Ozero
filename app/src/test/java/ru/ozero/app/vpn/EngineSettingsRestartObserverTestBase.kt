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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.TrafficMode

@OptIn(ExperimentalCoroutinesApi::class)
abstract class EngineSettingsRestartObserverTestBase {

    protected val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    protected fun newObserver(
        flow: MutableSharedFlow<SettingsModel>,
        stateProvider: () -> TunnelState,
    ): EngineSettingsRestartObserver = EngineSettingsRestartObserver(
        settingsFlow = flow,
        vpnStateProvider = stateProvider,
        onRestartConnected = {},
    )

    protected fun newObserver(
        state: MutableStateFlow<TunnelState>,
        onRestartConnected: (EngineSettingsRestartObserver.Snapshot) -> Unit,
    ): EngineSettingsRestartObserver = EngineSettingsRestartObserver(
        settingsFlow = MutableSharedFlow(replay = 0, extraBufferCapacity = 8),
        vpnStateProvider = { state.value },
        onRestartConnected = onRestartConnected,
    )

    protected fun snapshot(
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

    protected fun trigger(
        previous: EngineSettingsRestartObserver.Snapshot,
        snapshot: EngineSettingsRestartObserver.Snapshot,
    ) = EngineSettingsRestartObserver.Trigger(previous = previous, snapshot = snapshot)

    protected fun alwaysConnected(): () -> TunnelState = {
        TunnelState.Connected(engineId = EngineId.BYEDPI, socksPort = 1080)
    }

    protected fun TestScope.advanceRestartDebounce() {
        runCurrent()
        advanceTimeBy(EngineSettingsRestartObserver.RESTART_DEBOUNCE_MS_FOR_TESTS)
        runCurrent()
    }

    protected fun TestScope.collectTriggers(
        observer: EngineSettingsRestartObserver,
        collected: MutableList<EngineSettingsRestartObserver.Trigger>,
    ): Job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        observer.triggers.toList(collected)
    }
}
