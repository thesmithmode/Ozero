package ru.ozero.app.vpn

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.ozero.commonvpn.OzeroVpnService
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.ByeDpiUiSettings
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.settings.TrafficMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuntimeManualEngineSwitchTest {

    @Test
    fun `manual engine settings change restarts connected tunnel`() = runTest {
        val startServiceActions = mutableListOf<String?>()
        val settings = MutableStateFlow(SettingsModel(manualEngine = EngineId.WARP))
        val tunnelController = TunnelController()
        tunnelController.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val coordinator = RuntimeConfigRestartCoordinator(
            context = recordingContext(startServiceActions) {
                tunnelController.setState(TunnelState.Disconnecting)
                launch { tunnelController.setState(TunnelState.Connected(EngineId.FPTN, 0)) }
            },
            observer = EngineRuntimeConfigRestartObserver(emptySet()),
            tunnelController = tunnelController,
            settingsRepository = fakeSettingsRepository(settings),
        )

        coordinator.start(backgroundScope)
        runCurrent()
        settings.value = SettingsModel(manualEngine = EngineId.FPTN)
        advanceTimeBy(EngineSettingsRestartObserver.RESTART_DEBOUNCE_MS_FOR_TESTS)
        runCurrent()

        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), startServiceActions)
        assertEquals(TunnelState.Connected(EngineId.FPTN, 0), tunnelController.state.value)
    }

    @Test
    fun `manual switch to Adaptive VPN uses the target deadline not the previous engine deadline`() = runTest {
        val actions = mutableListOf<String?>()
        val settings = MutableStateFlow(SettingsModel(manualEngine = EngineId.WARP))
        val controller = TunnelController(watchdogScope = backgroundScope)
        controller.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val coordinator = RuntimeConfigRestartCoordinator(
            context = recordingContext(actions) { controller.setState(TunnelState.Disconnecting) },
            observer = EngineRuntimeConfigRestartObserver(emptySet()),
            tunnelController = controller,
            settingsRepository = fakeSettingsRepository(settings),
        )

        coordinator.start(backgroundScope)
        runCurrent()
        settings.value = SettingsModel(manualEngine = EngineId.GFVPN)
        advanceTimeBy(EngineSettingsRestartObserver.RESTART_DEBOUNCE_MS_FOR_TESTS)
        runCurrent()
        advanceTimeBy(300_000L)
        assertEquals(EngineId.GFVPN, controller.switching.value?.to)
        assertTrue(coordinator.restartInProgress())
        controller.setState(TunnelState.Connected(EngineId.GFVPN, 0))
        runCurrent()
        assertFalse(coordinator.restartInProgress())
        assertEquals(null, controller.switching.value)
        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), actions)
    }

    @Test
    fun `automatic switch waits for fallback and accepts any connected candidate`() = runTest {
        val actions = mutableListOf<String?>()
        val settings = MutableStateFlow(SettingsModel(manualEngine = EngineId.WARP))
        val controller = TunnelController(watchdogScope = backgroundScope)
        controller.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val coordinator = RuntimeConfigRestartCoordinator(
            context = recordingContext(actions) { controller.onDisconnecting() },
            observer = EngineRuntimeConfigRestartObserver(emptySet()),
            tunnelController = controller,
            settingsRepository = fakeSettingsRepository(settings),
        )

        coordinator.start(backgroundScope)
        runCurrent()
        settings.value = SettingsModel(
            manualEngine = null,
            engineAutoPriority = listOf(EngineId.BYEDPI, EngineId.MASQUE),
        )
        advanceTimeBy(EngineSettingsRestartObserver.RESTART_DEBOUNCE_MS_FOR_TESTS)
        runCurrent()
        controller.reset()
        controller.onProbing(EngineId.BYEDPI)
        advanceTimeBy(30_000L)
        controller.onDisconnecting()
        controller.reset()
        controller.onProbing(EngineId.MASQUE)
        advanceTimeBy(70_000L)
        assertTrue(coordinator.restartInProgress())
        assertEquals(EngineId.WARP, controller.switching.value?.from)
        assertEquals(null, controller.switching.value?.to)
        controller.onConnecting(EngineId.MASQUE)
        controller.onEngineStarted(EngineId.MASQUE, 12345)
        runCurrent()

        assertFalse(coordinator.restartInProgress())
        assertEquals(null, controller.switching.value)
        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), actions)
    }

    @Test
    fun `automatic switch remains bounded and excludes Telegram standalone candidate`() = runTest {
        val actions = mutableListOf<String?>()
        val settings = MutableStateFlow(SettingsModel(manualEngine = EngineId.WARP))
        val controller = TunnelController(watchdogScope = backgroundScope)
        controller.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val coordinator = RuntimeConfigRestartCoordinator(
            context = recordingContext(actions) { controller.onDisconnecting() },
            observer = EngineRuntimeConfigRestartObserver(emptySet()),
            tunnelController = controller,
            settingsRepository = fakeSettingsRepository(settings),
        )

        coordinator.start(backgroundScope)
        runCurrent()
        settings.value = SettingsModel(
            manualEngine = null,
            engineAutoPriority = listOf(EngineId.BYEDPI, EngineId.BYEDPI, EngineId.TGWSS),
        )
        advanceTimeBy(EngineSettingsRestartObserver.RESTART_DEBOUNCE_MS_FOR_TESTS)
        runCurrent()
        controller.reset()
        controller.onProbing(EngineId.BYEDPI)
        runCurrent()
        advanceTimeBy(59_999L)
        assertTrue(coordinator.restartInProgress())
        advanceTimeBy(2L)
        runCurrent()

        assertFalse(coordinator.restartInProgress())
        assertEquals(null, controller.switching.value)
        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), actions)
    }

    @Test
    fun `automatic switch without startup progress retains the original short timeout`() = runTest {
        val actions = mutableListOf<String?>()
        val settings = MutableStateFlow(SettingsModel(manualEngine = EngineId.WARP))
        val controller = TunnelController(watchdogScope = backgroundScope)
        controller.setState(TunnelState.Connected(EngineId.WARP, 51820))
        val coordinator = RuntimeConfigRestartCoordinator(
            context = recordingContext(actions) { controller.onDisconnecting() },
            observer = EngineRuntimeConfigRestartObserver(emptySet()),
            tunnelController = controller,
            settingsRepository = fakeSettingsRepository(settings),
        )

        coordinator.start(backgroundScope)
        runCurrent()
        settings.value = SettingsModel(
            manualEngine = null,
            engineAutoPriority = listOf(EngineId.BYEDPI),
        )
        advanceTimeBy(EngineSettingsRestartObserver.RESTART_DEBOUNCE_MS_FOR_TESTS)
        runCurrent()
        advanceTimeBy(14_999L)
        assertTrue(coordinator.restartInProgress())
        advanceTimeBy(2L)
        runCurrent()

        assertFalse(coordinator.restartInProgress())
        assertEquals(null, controller.switching.value)
        assertEquals(listOf<String?>(OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG), actions)
    }

    private fun recordingContext(
        startServiceActions: MutableList<String?>,
        onStartService: () -> Unit = {},
    ): Context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun startService(intent: Intent): android.content.ComponentName? {
            startServiceActions += intent.action
            onStartService()
            return null
        }
    }

    private fun RuntimeConfigRestartCoordinator.restartInProgress(): Boolean {
        val field = javaClass.getDeclaredField("restartInProgress")
        field.isAccessible = true
        return field.getBoolean(this)
    }

    private fun fakeSettingsRepository(
        flow: Flow<SettingsModel> = MutableStateFlow(SettingsModel()),
    ): SettingsRepository = object : SettingsRepository {
        override val settings: Flow<SettingsModel> = flow
        override suspend fun setSplitMode(mode: SplitTunnelMode) = Unit
        override suspend fun setIpv6Enabled(enabled: Boolean) = Unit
        override suspend fun setAutoStart(enabled: Boolean) = Unit
        override suspend fun setTrafficMode(mode: TrafficMode) = Unit
        override suspend fun setManualEngine(engine: EngineId?) = Unit
        override suspend fun setEngineAutoPriority(priority: List<EngineId>) = Unit
        override suspend fun setUrnetworkEnabled(enabled: Boolean) = Unit
        override suspend fun setUrnetworkJwt(jwt: String?) = Unit
        override suspend fun setUrnetworkCountryCode(code: String?) = Unit
        override suspend fun setByedpiWinningArgs(args: String?) = Unit
        override suspend fun setByedpiDefaultAccepted(accepted: Boolean) = Unit
        override suspend fun setByedpiUseUiMode(enabled: Boolean) = Unit
        override suspend fun setByedpiUiSettings(settings: ByeDpiUiSettings) = Unit
        override suspend fun setCustomDnsServers(servers: List<String>) = Unit
        override suspend fun setHostsMode(mode: HostsMode) = Unit
        override suspend fun setHosts(hosts: List<String>) = Unit
        override suspend fun setUiLocaleTag(tag: String?) = Unit
        override suspend fun setAppMode(mode: AppMode) = Unit
        override suspend fun setKillswitchEnabled(enabled: Boolean) = Unit
        override suspend fun setAlwaysOnBannerDismissed(dismissed: Boolean) = Unit
    }

    private fun TunnelController.setState(state: TunnelState) {
        val field = javaClass.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(this) as kotlinx.coroutines.flow.MutableStateFlow<TunnelState>
        flow.value = state
    }
}
