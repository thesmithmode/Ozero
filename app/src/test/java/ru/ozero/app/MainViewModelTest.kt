package ru.ozero.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.app.ui.MainViewModel
import ru.ozero.commonvpn.HealthMonitor
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.commonvpn.TunnelStats
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.LocationsViewController
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var tunnelController: TunnelController
    private lateinit var healthMonitor: HealthMonitor
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var viewModel: MainViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        tunnelController = TunnelController()
        healthMonitor = HealthMonitor()
        settingsRepository = FakeSettingsRepository()
        viewModel = MainViewModel(tunnelController, healthMonitor, settingsRepository, FakeUrnetworkBridge())
    }

    private class FakeUrnetworkBridge : UrnetworkSdkBridge {
        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byClientJwt: String,
        ): UrnetworkSdkBridge.StartResult = UrnetworkSdkBridge.StartResult.Success
        override suspend fun stop() = Unit
        override fun isRunning(): Boolean = false
        override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult =
            UrnetworkSdkBridge.AttachResult.Success
        override fun connectTo(location: ConnectLocation) = Unit
        override fun connectBestAvailable() = Unit
        override fun selectedLocation(): ConnectLocation? = null
        override fun openLocationsViewController(): LocationsViewController? = null
        override fun setProvidePaused(paused: Boolean) = Unit
        override fun isProvidePaused(): Boolean = true
        override fun peerCount(): Int = 0
        override fun unpaidByteCount(): Long = 0L
        override fun fetchTransferStats() = Unit
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsIdle() {
        assertIs<TunnelState.Idle>(viewModel.state.value)
    }

    @Test
    fun stateMirrorsTunnelControllerProbing() = runTest {
        tunnelController.onProbing()
        advanceUntilIdle()
        assertIs<TunnelState.Probing>(viewModel.state.value)
    }

    @Test
    fun stateMirrorsTunnelControllerConnected() = runTest {
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        advanceUntilIdle()
        assertIs<TunnelState.Connected>(viewModel.state.value)
    }

    @Test
    fun onVpnPermissionDeniedFromProbingMovesToFailed() = runTest {
        tunnelController.onProbing()
        advanceUntilIdle()
        viewModel.onVpnPermissionDenied()
        advanceUntilIdle()
        assertIs<TunnelState.Failed>(tunnelController.state.value)
    }

    @Test
    fun onVpnPermissionDeniedFromConnectingMovesToFailed() = runTest {
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        advanceUntilIdle()
        viewModel.onVpnPermissionDenied()
        advanceUntilIdle()
        assertIs<TunnelState.Failed>(tunnelController.state.value)
    }

    @Test
    fun onVpnPermissionDeniedFromIdleIsNoOp() = runTest {
        viewModel.onVpnPermissionDenied()
        advanceUntilIdle()
        assertIs<TunnelState.Idle>(tunnelController.state.value)
    }

    @Test
    fun statsInitiallyNull() {
        assertNull(viewModel.stats.value)
    }

    @Test
    fun statsMirrorsTunnelController() = runTest {
        val snapshot = TunnelStats(
            txPackets = 5,
            txBytes = 256,
            rxPackets = 10,
            rxBytes = 1024,
            timestampMs = 42,
        )
        tunnelController.updateStats(snapshot)
        advanceUntilIdle()
        assertEquals(snapshot, viewModel.stats.value)
    }

    @Test
    fun healthStatusInitiallyUnknown() {
        assertEquals(HealthMonitor.Status.UNKNOWN, viewModel.healthStatus.value)
    }

    @Test
    fun appModeDefaultIsSimple() {
        assertEquals(AppMode.SIMPLE, viewModel.appMode.value)
    }

    @Test
    fun appModeMirrorsSettingsRepository() = runTest {
        settingsRepository.emit(SettingsModel.DEFAULT.copy(appMode = AppMode.EXPERT))
        advanceUntilIdle()
        assertEquals(AppMode.EXPERT, viewModel.appMode.value)
    }

    @Test
    fun manualEngineDefaultIsNull() {
        assertNull(viewModel.manualEngine.value)
    }

    @Test
    fun manualEngineMirrorsSettingsRepository() = runTest {
        settingsRepository.emit(SettingsModel.DEFAULT.copy(manualEngine = EngineId.BYEDPI))
        advanceUntilIdle()
        assertEquals(EngineId.BYEDPI, viewModel.manualEngine.value)
    }

    @Test
    fun onManualEngineSelectForwardsToRepository() = runTest {
        viewModel.onManualEngineSelect(EngineId.BYEDPI)
        advanceUntilIdle()
        assertEquals(listOf<EngineId?>(EngineId.BYEDPI), settingsRepository.manualEngineUpdates)
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val state = MutableStateFlow(SettingsModel.DEFAULT)
        override val settings: Flow<SettingsModel> = state.asStateFlow()

        val manualEngineUpdates = mutableListOf<EngineId?>()

        fun emit(model: SettingsModel) {
            state.value = model
        }

        override suspend fun setSplitMode(mode: SplitTunnelMode) = Unit
        override suspend fun setIpv6Enabled(enabled: Boolean) = Unit
        override suspend fun setAutoStart(enabled: Boolean) = Unit
        override suspend fun setManualEngine(engine: EngineId?) {
            manualEngineUpdates += engine
        }
        override suspend fun setUrnetworkEnabled(enabled: Boolean) = Unit
        override suspend fun setUrnetworkJwt(jwt: String?) = Unit
        override suspend fun setByedpiWinningArgs(args: String?) = Unit
        override suspend fun setCustomDnsServers(servers: List<String>) = Unit
        override suspend fun setHostsMode(mode: HostsMode) = Unit
        override suspend fun setHosts(hosts: List<String>) = Unit
        override suspend fun setUiLocaleTag(tag: String?) = Unit
        override suspend fun setAppMode(mode: AppMode) = Unit
    }
}
