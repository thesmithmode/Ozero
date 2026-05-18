package ru.ozero.app

import com.bringyour.sdk.LocationsViewController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
import ru.ozero.enginescore.EngineCapabilities
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.IpProbeRoute
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.settings.AppMode
import ru.ozero.enginescore.settings.HostsMode
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkWindowType
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
    private lateinit var byedpiPlugin: FakeEnginePlugin
    private lateinit var warpPlugin: FakeEnginePlugin
    private lateinit var urnetworkPlugin: FakeEnginePlugin
    private lateinit var urnetworkBridge: FakeUrnetworkBridge
    private lateinit var viewModel: MainViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        tunnelController = TunnelController()
        healthMonitor = HealthMonitor()
        settingsRepository = FakeSettingsRepository()
        ipInfoProvider = FakeIpInfoProvider()
        byedpiPlugin = FakeEnginePlugin(EngineId.BYEDPI) { port ->
            IpProbeRoute.Socks("127.0.0.1", if (port > 0) port else 1080)
        }
        warpPlugin = FakeEnginePlugin(EngineId.WARP) {
            IpProbeRoute.StaticLocation(country = "Cloudflare WARP", countryCode = null)
        }
        urnetworkPlugin = FakeEnginePlugin(EngineId.URNETWORK) {
            IpProbeRoute.Unavailable("URnetwork location pending")
        }
        urnetworkBridge = FakeUrnetworkBridge()
        viewModel = MainViewModel(
            tunnelController,
            healthMonitor,
            settingsRepository,
            urnetworkBridge,
            ipInfoProvider,
            setOf(byedpiPlugin, warpPlugin, urnetworkPlugin),
        )
    }

    private fun newViewModel(
        bridge: UrnetworkSdkBridge = urnetworkBridge,
        plugins: Set<EnginePlugin> = setOf(byedpiPlugin, warpPlugin, urnetworkPlugin),
    ): MainViewModel = MainViewModel(
        tunnelController,
        healthMonitor,
        settingsRepository,
        bridge,
        ipInfoProvider,
        plugins,
    )

    private class FakeEnginePlugin(
        override val id: EngineId,
        private val route: (Int) -> IpProbeRoute,
    ) : EnginePlugin {
        override val capabilities: EngineCapabilities = EngineCapabilities(
            supportsTcp = true,
            supportsUdp = false,
            supportsDoH = false,
            localOnly = true,
            requiresServer = false,
            supportsUpstreamSocks = false,
        )
        override suspend fun start(config: EngineConfig, upstream: Upstream): StartResult =
            StartResult.Failure("test fake")
        override suspend fun stop() = Unit
        override suspend fun probe(): ProbeResult = ProbeResult.Failure("test fake")
        override fun stats(): Flow<EngineStats> = emptyFlow()
        override suspend fun ipProbeRoute(socksPort: Int): IpProbeRoute = route(socksPort)
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

        @Volatile var fetchCalls: Int = 0

        @Volatile var fetchViaCalls: Int = 0

        @Volatile var lastSocksHost: String? = null

        @Volatile var lastSocksPort: Int? = null

        @Volatile var lastSocketFactoryUsed: Boolean = false

        override suspend fun fetch(): Result<IpInfo> {
            calls += 1
            fetchCalls += 1
            return result
        }
        override suspend fun fetchVia(socksHost: String?, socksPort: Int?): Result<IpInfo> {
            calls += 1
            fetchViaCalls += 1
            lastSocksHost = socksHost
            lastSocksPort = socksPort
            return result
        }
        override suspend fun fetchViaSocketFactory(
            socketFactory: javax.net.SocketFactory?,
        ): Result<IpInfo> {
            calls += 1
            lastSocketFactoryUsed = socketFactory != null
            return result
        }
    }

    private class FakeUrnetworkBridge(var peers: Int = 0, var running: Boolean = true) : UrnetworkSdkBridge {
        var applyCalls: Int = 0
        var lastWindowType: UrnetworkWindowType? = null
        var lastFixedIp: Boolean? = null
        var lastAllowDirect: Boolean? = null

        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byClientJwt: String,
        ): UrnetworkSdkBridge.StartResult = UrnetworkSdkBridge.StartResult.Success
        override suspend fun stop() = Unit
        override fun isRunning(): Boolean = running
        override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult =
            UrnetworkSdkBridge.AttachResult.Success
        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit
        override fun connectBestAvailable() = Unit
        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = null
        override fun openLocationsViewController(): LocationsViewController? = null
        override fun setProvidePaused(paused: Boolean) = Unit
        override fun isProvidePaused(): Boolean = true
        override fun peerCount(): Int = peers
        override fun unpaidByteCount(): Long = 0L
        override fun fetchTransferStats() = Unit
        override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
        override fun applyPerformanceProfile(
            windowType: UrnetworkWindowType,
            fixedIpSize: Boolean,
            allowDirect: Boolean,
        ) {
            applyCalls += 1
            lastWindowType = windowType
            lastFixedIp = fixedIpSize
            lastAllowDirect = allowDirect
        }
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
    fun speedHistoryRetainedDuringSwitching() = runTest {
        val sample = TunnelStats(txPackets = 1, txBytes = 100, rxPackets = 2, rxBytes = 200, timestampMs = 1)
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        tunnelController.updateStats(sample)
        advanceUntilIdle()
        val historyDuringConnected = viewModel.speedHistory.value
        assert(historyDuringConnected.isNotEmpty()) { "speedHistory должна заполниться на updateStats" }

        tunnelController.onSwitchingStarted(EngineId.BYEDPI, EngineId.WARP)
        tunnelController.onDisconnecting()
        tunnelController.reset()
        advanceUntilIdle()

        assertEquals(
            historyDuringConnected,
            viewModel.speedHistory.value,
            "speedHistory НЕ должна сбрасываться когда switching активен — это причина прыжков графика",
        )
    }

    @Test
    fun speedHistoryClearedWhenNotSwitching() = runTest {
        val sample = TunnelStats(txPackets = 1, txBytes = 100, rxPackets = 2, rxBytes = 200, timestampMs = 1)
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        tunnelController.updateStats(sample)
        advanceUntilIdle()
        assert(viewModel.speedHistory.value.isNotEmpty())

        tunnelController.onDisconnecting()
        tunnelController.reset()
        advanceUntilIdle()

        assert(viewModel.speedHistory.value.isEmpty()) {
            "без активного switching speedHistory обязана очиститься на reset (stats=null)"
        }
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
    fun urnetworkPeerSearchSecondsZeroDuringGracePeriod() = runTest {
        val bridge = FakeUrnetworkBridge(peers = 0)
        val vm = newViewModel(bridge = bridge)
        backgroundScope.launch { vm.urnetworkPeerSearchSeconds.collect {} }
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.URNETWORK)
        tunnelController.onEngineStarted(EngineId.URNETWORK, 1080)
        runCurrent()
        advanceTimeBy(9_500)
        runCurrent()
        assertEquals(0, vm.urnetworkPeerSearchSeconds.value)
    }

    @Test
    fun urnetworkPeerSearchSecondsIncrementsAfterGracePeriod() = runTest {
        val bridge = FakeUrnetworkBridge(peers = 0)
        val vm = newViewModel(bridge = bridge)
        backgroundScope.launch { vm.urnetworkPeerSearchSeconds.collect {} }
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.URNETWORK)
        tunnelController.onEngineStarted(EngineId.URNETWORK, 1080)
        runCurrent()
        advanceTimeBy(14_500)
        runCurrent()
        assertEquals(5, vm.urnetworkPeerSearchSeconds.value)
    }

    @Test
    fun urnetworkPeerSearchSecondsResetsWhenPeersAppear() = runTest {
        val bridge = FakeUrnetworkBridge(peers = 0)
        val vm = newViewModel(bridge = bridge)
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
    fun ipInfoFetchesViaSocksOnByedpiConnected() = runTest {
        backgroundScope.launch { viewModel.ipInfo.collect {} }
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        advanceUntilIdle()
        kotlinx.coroutines.delay(2_500)
        advanceUntilIdle()
        val s = viewModel.ipInfo.value
        assertIs<IpInfoState.Loaded>(s)
        assertEquals("203.0.113.1", s.info.ip)
        assertEquals(1, ipInfoProvider.calls)
        assertEquals(1, ipInfoProvider.fetchViaCalls)
        assertEquals("127.0.0.1", ipInfoProvider.lastSocksHost)
        assertEquals(1080, ipInfoProvider.lastSocksPort)
    }

    @Test
    fun ipInfoStaticLocationForWarp() = runTest {
        backgroundScope.launch { viewModel.ipInfo.collect {} }
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.WARP)
        tunnelController.onEngineStarted(EngineId.WARP, 0)
        advanceUntilIdle()
        kotlinx.coroutines.delay(2_500)
        advanceUntilIdle()
        val s = viewModel.ipInfo.value
        val loaded = assertIs<IpInfoState.Loaded>(
            s,
            "WARP — ipProbeRoute=StaticLocation('Cloudflare WARP') → IpInfoState.Loaded без HTTP fetch. " +
                "Архитектура: excludeSelf=true для всех движков (split tunnel ALL должен работать) → " +
                "self-fetch бы вернул реальный IP устройства, поэтому WARP override'ит ipProbeRoute " +
                "на StaticLocation вместо Default. Регрессия commit 5a8089dd: WARP полагался на " +
                "excludeSelf=false для self-traffic через TUN → ломал per-app VPN mode.",
        )
        assertEquals("Cloudflare WARP", loaded.info.country)
        assertEquals(0, ipInfoProvider.fetchCalls)
        assertEquals(0, ipInfoProvider.fetchViaCalls)
        assertNull(ipInfoProvider.lastSocksHost)
    }

    @Test
    fun ipInfoErrorForUrnetworkWhenLocationUnavailable() = runTest {
        backgroundScope.launch { viewModel.ipInfo.collect {} }
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.URNETWORK)
        tunnelController.onEngineStarted(EngineId.URNETWORK, 0)
        runCurrent()
        // 3000ms warmup + 2×1500ms retry delays = 6000ms total
        advanceTimeBy(7_000)
        runCurrent()
        val s = viewModel.ipInfo.value
        assertIs<IpInfoState.Error>(
            s,
            "URnetwork без selectedLocation → ipProbeRoute.Unavailable → IpInfoState.Error. " +
                "Sentinel: 0 HTTP вызовов, error.message содержит причину.",
        )
        assertEquals(0, ipInfoProvider.calls)
    }

    @Test
    fun ipInfoStaticLocationForUrnetworkWhenLocationKnown() = runTest {
        val staticUrnetwork = FakeEnginePlugin(EngineId.URNETWORK) {
            IpProbeRoute.StaticLocation(country = "Germany", countryCode = "DE")
        }
        val vm = newViewModel(plugins = setOf(byedpiPlugin, warpPlugin, staticUrnetwork))
        backgroundScope.launch { vm.ipInfo.collect {} }
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.URNETWORK)
        tunnelController.onEngineStarted(EngineId.URNETWORK, 0)
        runCurrent()
        advanceTimeBy(4_500)
        runCurrent()
        val s = vm.ipInfo.value
        assertIs<IpInfoState.Loaded>(s)
        assertEquals("", s.info.ip, "StaticLocation не несёт IP — только страну.")
        assertEquals("Germany", s.info.country)
        assertEquals("DE", s.info.countryCode)
        assertEquals(0, ipInfoProvider.calls, "StaticLocation не должен делать HTTP запросов.")
    }

    @Test
    fun ipInfoUpdatesWhenUrnetworkLocationChangesDuringSession() = runTest {
        var countryCode = "US"
        val dynamicUrnetwork = FakeEnginePlugin(EngineId.URNETWORK) {
            IpProbeRoute.StaticLocation(country = "Country", countryCode = countryCode)
        }
        val vm = newViewModel(plugins = setOf(byedpiPlugin, warpPlugin, dynamicUrnetwork))
        backgroundScope.launch { vm.ipInfo.collect {} }
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.URNETWORK)
        tunnelController.onEngineStarted(EngineId.URNETWORK, 0)
        runCurrent()
        advanceTimeBy(4_500)
        runCurrent()
        assertEquals("US", (vm.ipInfo.value as IpInfoState.Loaded).info.countryCode)
        countryCode = "DE"
        advanceTimeBy(4_000)
        runCurrent()
        val s = vm.ipInfo.value
        assertIs<IpInfoState.Loaded>(s)
        assertEquals("DE", s.info.countryCode)
        assertEquals(0, ipInfoProvider.calls, "StaticLocation — нет HTTP запросов.")
    }

    @Test
    fun ipInfoBackToIdleOnDisconnect() = runTest {
        backgroundScope.launch { viewModel.ipInfo.collect {} }
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        advanceUntilIdle()
        kotlinx.coroutines.delay(2_500)
        advanceUntilIdle()
        tunnelController.onEngineDied(EngineId.BYEDPI, "test disconnect")
        advanceUntilIdle()
        assertIs<IpInfoState.Idle>(viewModel.ipInfo.value)
    }

    @Test
    fun ipInfoErrorOnProviderFailure() = runTest {
        backgroundScope.launch { viewModel.ipInfo.collect {} }
        ipInfoProvider.result = Result.failure(java.io.IOException("network down"))
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        advanceUntilIdle()
        kotlinx.coroutines.delay(6_000)
        advanceUntilIdle()
        val s = viewModel.ipInfo.value
        assertIs<IpInfoState.Error>(s)
        assertEquals("network down", s.message)
    }

    @Test
    fun ipInfoNotRefetchedOnSameSession() = runTest {
        backgroundScope.launch { viewModel.ipInfo.collect {} }
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        advanceUntilIdle()
        kotlinx.coroutines.delay(2_500)
        advanceUntilIdle()
        assertEquals(1, ipInfoProvider.calls)
    }

    @Test
    fun refreshIpInfoForcesRefetch() = runTest {
        backgroundScope.launch { viewModel.ipInfo.collect {} }
        viewModel.refreshIpInfo()
        advanceUntilIdle()
        val s = viewModel.ipInfo.value
        assertIs<IpInfoState.Loaded>(s)
        assertEquals(1, ipInfoProvider.calls)
    }

    @Test
    fun isReconnectingFalseInitially() = runTest {
        advanceUntilIdle()
        assertEquals(false, viewModel.isReconnecting.value)
    }

    @Test
    fun isReconnectingFalseOnFirstConnect() = runTest {
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        advanceUntilIdle()
        assertEquals(false, viewModel.isReconnecting.value)
    }

    @Test
    fun isReconnectingTrueAfterConnectedFailed() = runTest {
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        advanceUntilIdle()
        tunnelController.onEngineDied(EngineId.BYEDPI, "network loss")
        advanceUntilIdle()
        assertEquals(true, viewModel.isReconnecting.value)
    }

    @Test
    fun isReconnectingStaysTrueOnFailedThenProbingThenConnecting() = runTest {
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        advanceUntilIdle()
        tunnelController.onEngineDied(EngineId.BYEDPI, "lost")
        advanceUntilIdle()
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        advanceUntilIdle()
        assertEquals(true, viewModel.isReconnecting.value)
    }

    @Test
    fun isReconnectingClearsOnReconnectSuccess() = runTest {
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        advanceUntilIdle()
        tunnelController.onEngineDied(EngineId.BYEDPI, "lost")
        advanceUntilIdle()
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        advanceUntilIdle()
        assertEquals(false, viewModel.isReconnecting.value)
    }

    @Test
    fun isReconnectingFalseAfterUserDisconnect() = runTest {
        tunnelController.onProbing()
        tunnelController.onConnecting(EngineId.BYEDPI)
        tunnelController.onEngineStarted(EngineId.BYEDPI, 1080)
        advanceUntilIdle()
        tunnelController.onDisconnecting()
        tunnelController.reset()
        advanceUntilIdle()
        assertEquals(false, viewModel.isReconnecting.value)
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
        override suspend fun setUrnetworkCountryCode(code: String?) = Unit
        override suspend fun setByedpiWinningArgs(args: String?) = Unit
        override suspend fun setByedpiDefaultAccepted(accepted: Boolean) = Unit
        override suspend fun setCustomDnsServers(servers: List<String>) = Unit
        override suspend fun setHostsMode(mode: HostsMode) = Unit
        override suspend fun setHosts(hosts: List<String>) = Unit
        override suspend fun setUiLocaleTag(tag: String?) = Unit
        override suspend fun setAppMode(mode: AppMode) = Unit
        override suspend fun setKillswitchEnabled(enabled: Boolean) = Unit
        override suspend fun setAlwaysOnBannerDismissed(dismissed: Boolean) = Unit
    }
}
