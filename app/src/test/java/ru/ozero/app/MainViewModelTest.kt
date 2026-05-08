package ru.ozero.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.app.ui.IpInfoState
import ru.ozero.app.ui.MainViewModel
import ru.ozero.commonnet.IpInfo
import ru.ozero.commonnet.IpInfoProvider
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
    private lateinit var ipInfoProvider: FakeIpInfoProvider
    private lateinit var viewModel: MainViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        tunnelController = TunnelController()
        healthMonitor = HealthMonitor()
        settingsRepository = FakeSettingsRepository()
        ipInfoProvider = FakeIpInfoProvider()
        viewModel = MainViewModel(
            tunnelController,
            healthMonitor,
            settingsRepository,
            FakeUrnetworkBridge(),
            ipInfoProvider,
        )
    }

    private class FakeIpInfoProvider(
        @Volatile var result: Result<IpInfo> = Result.success(
            IpInfo(
                ip = "203.0.113.1",
                country = "Germany",
                countryCode = "DE",
                city = "Berlin",
                fetchedAtMs = 1_700_000_000_000L,
            ),
        ),
    ) : IpInfoProvider {
        @Volatile var calls: Int = 0
        override suspend fun fetch(): Result<IpInfo> {
            calls += 1
            return result
        }
    }

    private class FakeUrnetworkBridge(var peers: Int = 0) : UrnetworkSdkBridge {
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
        override fun peerCount(): Int = peers
        override fun unpaidByteCount(): Long = 0L
        override fun fetchTransferStats() = Unit
        override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
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

    @Test
    fun urnetworkPeerSearchSecondsZeroWhenIdle() = runTest {
        backgroundScope.launch { viewModel.urnetworkPeerSearchSeconds.collect {} }
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(0, viewModel.urnetworkPeerSearchSeconds.value)
    }

    @Test
    fun urnetworkPeerSearchSecondsZeroWhenDifferentEngineConnected() = runTest {
        backgroundScope.launch { viewModel.urnetworkPeerSearchSeconds.collect {} }
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        runCurrent()
        advanceTimeBy(3_500)
        runCurrent()
        assertEquals(0, viewModel.urnetworkPeerSearchSeconds.value)
    }

    @Test
    fun urnetworkPeerSearchSecondsIncrementsWhileNoPeers() = runTest {
        val bridge = FakeUrnetworkBridge(peers = 0)
        val vm = MainViewModel(tunnelController, healthMonitor, settingsRepository, bridge, ipInfoProvider)
        backgroundScope.launch { vm.urnetworkPeerSearchSeconds.collect {} }
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.URNETWORK)
        tunnelController.onEngineStarted(EngineId.URNETWORK, 1080)
        runCurrent()
        advanceTimeBy(3_500)
        runCurrent()
        assert(vm.urnetworkPeerSearchSeconds.value >= 2) {
            "expected >=2 after 3.5s of no peers, got ${vm.urnetworkPeerSearchSeconds.value}"
        }
    }

    @Test
    fun urnetworkPeerSearchSecondsResetsWhenPeersAppear() = runTest {
        val bridge = FakeUrnetworkBridge(peers = 0)
        val vm = MainViewModel(tunnelController, healthMonitor, settingsRepository, bridge, ipInfoProvider)
        backgroundScope.launch { vm.urnetworkPeerSearchSeconds.collect {} }
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.URNETWORK)
        tunnelController.onEngineStarted(EngineId.URNETWORK, 1080)
        runCurrent()
        advanceTimeBy(3_500)
        runCurrent()
        bridge.peers = 5
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(0, vm.urnetworkPeerSearchSeconds.value)
    }

    @Test
    fun ipInfoInitiallyIdle() {
        assertIs<IpInfoState.Idle>(viewModel.ipInfo.value)
    }

    @Test
    fun ipInfoFetchesOnConnected() = runTest {
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        advanceUntilIdle()
        kotlinx.coroutines.delay(3_500)
        advanceUntilIdle()
        val s = viewModel.ipInfo.value
        assertIs<IpInfoState.Loaded>(s)
        assertEquals("203.0.113.1", s.info.ip)
        assertEquals(1, ipInfoProvider.calls)
    }

    @Test
    fun ipInfoBackToIdleOnDisconnect() = runTest {
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        advanceUntilIdle()
        kotlinx.coroutines.delay(3_500)
        advanceUntilIdle()
        tunnelController.onEngineDied(EngineId.BYEDPI, "test disconnect")
        advanceUntilIdle()
        assertIs<IpInfoState.Idle>(viewModel.ipInfo.value)
    }

    @Test
    fun ipInfoErrorOnProviderFailure() = runTest {
        ipInfoProvider.result = Result.failure(java.io.IOException("network down"))
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        advanceUntilIdle()
        kotlinx.coroutines.delay(3_500)
        advanceUntilIdle()
        val s = viewModel.ipInfo.value
        assertIs<IpInfoState.Error>(s)
        assertEquals("network down", s.message)
    }

    @Test
    fun ipInfoNotRefetchedOnSameSession() = runTest {
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        advanceUntilIdle()
        kotlinx.coroutines.delay(3_500)
        advanceUntilIdle()
        assertEquals(1, ipInfoProvider.calls)
    }

    @Test
    fun refreshIpInfoForcesRefetch() = runTest {
        viewModel.refreshIpInfo()
        advanceUntilIdle()
        val s = viewModel.ipInfo.value
        assertIs<IpInfoState.Loaded>(s)
        assertEquals(1, ipInfoProvider.calls)
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
        override suspend fun setKillswitchEnabled(enabled: Boolean) = Unit
    }
}
