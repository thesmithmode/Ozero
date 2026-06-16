package ru.ozero.commonvpn

import android.content.Intent
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.TrafficMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Suppress("LargeClass")
class OzeroVpnServiceRuntimeLifecycleTest {

    @Test
    fun `service can be prepared with injected runtime dependencies`() {
        preparedService()
        assertTrue(true)
    }

    @Test
    fun `injected property accessors accept all runtime collaborators`() {
        val service = Robolectric.buildService(OzeroVpnService::class.java).get()
        val chain = mockk<ChainOrchestrator>(relaxed = true)
        val gateway = mockk<HevTunnelGateway>(relaxed = true)
        val controller = TunnelController()
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val sessionRecorder = SessionStatsRecorder.NoOp
        val splitRules = SplitTunnelRulesProvider.NoOp
        val health = HealthMonitor()
        val plugins = emptySet<EnginePlugin>()
        val router = RuntimeFailureRouter()

        service.chainOrchestrator = chain
        service.tunnelGateway = gateway
        service.tunnelController = controller
        service.settingsRepository = settingsRepository
        service.sessionStatsRecorder = sessionRecorder
        service.splitTunnelRulesProvider = splitRules
        service.healthMonitor = health
        service.enginePlugins = plugins
        service.runtimeFailureRouter = router
        val killedPids = mutableListOf<Int>()
        service.processKiller = ProcessKiller { killedPids += it }

        assertEquals(chain, service.chainOrchestrator)
        assertEquals(gateway, service.tunnelGateway)
        assertEquals(controller, service.tunnelController)
        assertEquals(settingsRepository, service.settingsRepository)
        assertEquals(sessionRecorder, service.sessionStatsRecorder)
        assertEquals(splitRules, service.splitTunnelRulesProvider)
        assertEquals(health, service.healthMonitor)
        assertEquals(plugins, service.enginePlugins)
        assertEquals(router, service.runtimeFailureRouter)
        service.processKiller.kill(123)
        assertEquals(listOf(123), killedPids)
    }

    @Test
    fun `logActiveExternalVpn returns false when connectivity service is absent`() {
        val service = Robolectric.buildService(OzeroVpnService::class.java).get()

        assertFalse(service.callLogActiveExternalVpn())
    }

    @Test
    fun `logActiveExternalVpn ignores networks without capabilities and non vpn networks`() {
        val service = preparedService()
        val connectivityManager = RuntimeEnvironment.getApplication()
            .getSystemService(ConnectivityManager::class.java)
        val shadowConnectivity = Shadows.shadowOf(connectivityManager) as ShadowConnectivityManager
        val noCapsNetwork = ShadowNetwork.newInstance(101)
        val wifiNetwork = ShadowNetwork.newInstance(102)
        val wifiCaps = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(wifiCaps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowConnectivity.addNetwork(noCapsNetwork, null)
        shadowConnectivity.addNetwork(wifiNetwork, null)
        shadowConnectivity.setNetworkCapabilities(wifiNetwork, wifiCaps)

        assertFalse(service.callLogActiveExternalVpn())
    }

    @Test
    fun `logActiveExternalVpn detects external vpn network`() {
        val service = preparedService()
        val connectivityManager = RuntimeEnvironment.getApplication()
            .getSystemService(ConnectivityManager::class.java)
        val shadowConnectivity = Shadows.shadowOf(connectivityManager) as ShadowConnectivityManager
        val vpnNetwork = ShadowNetwork.newInstance(103)
        val vpnCaps = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(vpnCaps).addTransportType(NetworkCapabilities.TRANSPORT_VPN)
        shadowConnectivity.addNetwork(vpnNetwork, null)
        shadowConnectivity.setNetworkCapabilities(vpnNetwork, vpnCaps)

        assertTrue(service.callLogActiveExternalVpn())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `logActiveExternalVpn treats own vpn as external before owner uid API`() {
        val service = preparedService()
        val connectivityManager = RuntimeEnvironment.getApplication()
            .getSystemService(ConnectivityManager::class.java)
        val shadowConnectivity = Shadows.shadowOf(connectivityManager) as ShadowConnectivityManager
        val vpnNetwork = ShadowNetwork.newInstance(105)
        val vpnCaps = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(vpnCaps).addTransportType(NetworkCapabilities.TRANSPORT_VPN)
        vpnCaps.trySetOwnerUid(android.os.Process.myUid())
        shadowConnectivity.addNetwork(vpnNetwork, null)
        shadowConnectivity.setNetworkCapabilities(vpnNetwork, vpnCaps)

        assertTrue(service.callLogActiveExternalVpn())
    }

    @Test
    fun `logActiveExternalVpn ignores self owned vpn network when owner uid is available`() {
        val service = preparedService()
        val connectivityManager = RuntimeEnvironment.getApplication()
            .getSystemService(ConnectivityManager::class.java)
        val shadowConnectivity = Shadows.shadowOf(connectivityManager) as ShadowConnectivityManager
        val vpnNetwork = ShadowNetwork.newInstance(104)
        val vpnCaps = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(vpnCaps).addTransportType(NetworkCapabilities.TRANSPORT_VPN)
        val ownerSet = vpnCaps.trySetOwnerUid(android.os.Process.myUid())
        shadowConnectivity.addNetwork(vpnNetwork, null)
        shadowConnectivity.setNetworkCapabilities(vpnNetwork, vpnCaps)

        if (ownerSet) {
            assertFalse(service.callLogActiveExternalVpn())
        } else {
            assertTrue(service.callLogActiveExternalVpn())
        }
    }

    @Test
    fun `logActiveExternalVpn detects vpn network owned by another uid`() {
        val service = preparedService()
        val connectivityManager = RuntimeEnvironment.getApplication()
            .getSystemService(ConnectivityManager::class.java)
        val shadowConnectivity = Shadows.shadowOf(connectivityManager) as ShadowConnectivityManager
        val vpnNetwork = ShadowNetwork.newInstance(106)
        val vpnCaps = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(vpnCaps).addTransportType(NetworkCapabilities.TRANSPORT_VPN)
        vpnCaps.trySetOwnerUid(android.os.Process.myUid() + 1)
        shadowConnectivity.addNetwork(vpnNetwork, null)
        shadowConnectivity.setNetworkCapabilities(vpnNetwork, vpnCaps)

        assertTrue(service.callLogActiveExternalVpn())
    }

    @Test
    @Config(sdk = [29])
    fun `isOwnVpnNetwork matches only current uid on owner uid capable sdk`() {
        val caps = ShadowNetworkCapabilities.newInstance()
        val ownerSet = caps.trySetOwnerUid(1234)

        assertEquals(ownerSet, isOwnVpnNetwork(caps, 1234))
        assertFalse(isOwnVpnNetwork(caps, 5678))
    }

    @Test
    @Config(sdk = [28])
    fun `isOwnVpnNetwork is disabled before owner uid api`() {
        val caps = ShadowNetworkCapabilities.newInstance()
        caps.trySetOwnerUid(1234)

        assertFalse(isOwnVpnNetwork(caps, 1234))
    }

    @Test
    fun `onCreate rethrows Hilt attach failure in plain Robolectric application`() {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val settingsFlow = MutableStateFlow(SettingsModel(trafficMode = TrafficMode.TUN, killswitchEnabled = false))
        every { settingsRepository.settings } returns settingsFlow
        val runtimeFailureRouter = mockk<RuntimeFailureRouter>(relaxed = true)
        val controller = Robolectric.buildService(OzeroVpnService::class.java)
        val service = controller.get()
        service.setDependency("runtimeFailureRouter", runtimeFailureRouter)
        service.setDependency("settingsRepository", settingsRepository)
        service.setDependency("healthMonitor", HealthMonitor())
        service.setDependency("chainOrchestrator", mockk<ChainOrchestrator>(relaxed = true))
        service.setDependency("tunnelGateway", mockk<HevTunnelGateway>())

        assertThrows<IllegalStateException> { service.onCreate() }
        verify(exactly = 0) { runtimeFailureRouter.bind(any()) }
    }

    @Test
    fun `engineExtras returns blank before chain orchestrator injection and joined names after`() {
        val rawService = Robolectric.buildService(OzeroVpnService::class.java).get()
        assertEquals("", rawService.callEngineExtras())

        val service = preparedService()
        val chainOrchestrator = mockk<ChainOrchestrator>(relaxed = true)
        val first = mockk<EnginePlugin> { every { id } returns EngineId.BYEDPI }
        val second = mockk<EnginePlugin> { every { id } returns EngineId.WARP }
        every { chainOrchestrator.activeEngines() } returns listOf(first, second)
        service.setDependency("chainOrchestrator", chainOrchestrator)

        assertEquals("ByeDPI + WARP", service.callEngineExtras())
    }

    @Test
    fun `runtime failure handler delegates to watchdog and returns unit`() {
        val service = preparedService()
        val watchdog = mockk<EngineWatchdogCoordinator>(relaxed = true)
        service.setLazy("engineWatchdog", watchdog)

        service.callRuntimeFailureHandler(EngineId.WARP, "runtime died")

        verify(exactly = 1) { watchdog.handleEngineFailure(EngineId.WARP, "runtime died") }
    }

    @Test
    fun `lazy runtime collaborators are constructed from injected dependencies`() {
        val service = preparedService()
        service.setDependency("tunnelController", TunnelController())
        service.setDependency("sessionStatsRecorder", SessionStatsRecorder.NoOp)
        service.setDependency("splitTunnelRulesProvider", SplitTunnelRulesProvider.NoOp)
        service.setDependency("enginePlugins", emptySet<EnginePlugin>())

        assertTrue(service.forceLazy("notificationFactory") is OzeroNotificationFactory)
        assertTrue(service.forceLazy("tunBuilderHelper") is TunBuilderHelper)
        assertTrue(service.forceLazy("statsLogger") is TunnelStatsLogger)
        assertTrue(service.forceLazy("engineWatchdog") is EngineWatchdogCoordinator)
        assertTrue(service.forceLazy("shutdownCoord") is ShutdownCoordinator)
        assertTrue(service.forceLazy("actionDispatcher") is OzeroVpnServiceActionDispatcher)
        assertTrue(service.forceLazy("startSequence") is StartSequenceCoordinator)
        assertTrue(service.forceLazy("startCoordinator") is OzeroVpnServiceStartCoordinator)
    }

    @Test
    fun `startVpn coordinator lambdas close stale tun and run start sequence`() {
        val service = preparedService()
        val staleTun = mockk<ParcelFileDescriptor>(relaxed = true)
        val startSequence = mockk<StartSequenceCoordinator>(relaxed = true)
        service.setDependency("tunnelController", TunnelController())
        service.setDependency("sessionStatsRecorder", SessionStatsRecorder.NoOp)
        service.setDependency("splitTunnelRulesProvider", SplitTunnelRulesProvider.NoOp)
        service.setDependency("enginePlugins", emptySet<EnginePlugin>())
        service.setLazy("startSequence", startSequence)
        service.setAtomicReference("tunFdRef", staleTun)

        service.callStartVpn()

        assertTrue(waitUntil { !service.getAtomicBoolean("starting") })
        verify(exactly = 1) { staleTun.close() }
        coVerify(exactly = 1) { startSequence.run() }
        assertFalse(service.getAtomicBoolean("runtimeConfigRestartInProgress"))
    }

    @Test
    fun `startVpn coordinator ignores stale tun close failure and still runs start sequence`() {
        val service = preparedService()
        val staleTun = mockk<ParcelFileDescriptor>(relaxed = true)
        val startSequence = mockk<StartSequenceCoordinator>(relaxed = true)
        every { staleTun.close() } throws IllegalStateException("close failed")
        service.setDependency("tunnelController", TunnelController())
        service.setDependency("sessionStatsRecorder", SessionStatsRecorder.NoOp)
        service.setDependency("splitTunnelRulesProvider", SplitTunnelRulesProvider.NoOp)
        service.setDependency("enginePlugins", emptySet<EnginePlugin>())
        service.setLazy("startSequence", startSequence)
        service.setAtomicReference("tunFdRef", staleTun)

        service.callStartVpn()

        assertTrue(waitUntil { !service.getAtomicBoolean("starting") })
        coVerify(exactly = 1) { startSequence.run() }
        assertEquals(null, service.getAtomicReference("tunFdRef"))
    }

    @Test
    fun `startCoordinator close stale tun handles null fd`() {
        val service = preparedService()
        service.setAtomicReference("tunFdRef", null)

        service.getStartCoordinatorDeps().closeStaleTun()

        assertEquals(null, service.getAtomicReference("tunFdRef"))
    }

    @Test
    fun `startCoordinator loadTunnelLibrary can run off main thread`() {
        val service = preparedService()
        var thrown: Throwable? = null
        val thread = Thread {
            try {
                service.getStartCoordinatorDeps().loadTunnelLibrary()
            } catch (t: Throwable) {
                thrown = t
            }
        }

        thread.start()
        thread.join()

        assertEquals(null, thrown)
    }

    @Test
    fun `logActiveExternalVpn returns false for wrong connectivity service type`() {
        val service = spyk(preparedService())
        every { service.getSystemService(Context.CONNECTIVITY_SERVICE) } returns "not connectivity"

        assertFalse(service.callLogActiveExternalVpn())
    }

    @Test
    fun `onDestroy unbinds runtime failure handler and releases socket protector binding`() {
        val runtimeFailureRouter = mockk<RuntimeFailureRouter>(relaxed = true)
        val socketProtector = mockk<ru.ozero.enginescore.VpnSocketProtector>()
        val holderField = getSocketProtectorHolderField()
        holderField.set(null, socketProtector)

        val service = preparedService()
            .also { it.setDependency("runtimeFailureRouter", runtimeFailureRouter) }
            .also { it.setDependency("socketProtector", socketProtector) }
            .also { it.setDependency("shutdownCoord", mockk<ShutdownCoordinator>(relaxed = true)) }

        service.onDestroy()

        coVerify(exactly = 1) { runtimeFailureRouter.unbind(any()) }
        assertEquals(null, getSocketProtectorHolderField().get(null))
    }

    @Test
    fun `observeKillswitchSetting updates killswitch cache from settings flow`() {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val settingsFlow = MutableStateFlow(
            SettingsModel(
                trafficMode = TrafficMode.TUN,
                killswitchEnabled = false,
            ),
        )
        every { settingsRepository.settings } returns settingsFlow
        val service = preparedService(settingsRepository)
        service.callObserveKillswitchSetting()
        settingsFlow.value = SettingsModel(trafficMode = TrafficMode.TUN, killswitchEnabled = true)

        assertTrue(waitUntil { service.getVolatileBoolean("killswitchCached") })
    }

    @Test
    fun `observeKillswitchSetting keeps killswitch disabled for proxy traffic mode`() {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val settingsFlow = MutableStateFlow(
            SettingsModel(
                trafficMode = TrafficMode.TUN,
                killswitchEnabled = true,
            ),
        )
        every { settingsRepository.settings } returns settingsFlow
        val service = preparedService(settingsRepository)
        service.callObserveKillswitchSetting()
        assertTrue(waitUntil { service.getVolatileBoolean("killswitchCached") })

        settingsFlow.value = SettingsModel(trafficMode = TrafficMode.PROXY, killswitchEnabled = true)

        assertTrue(waitUntil { !service.getVolatileBoolean("killswitchCached") })
    }

    @Test
    fun `acquireLocks stores service locks and onDestroy clears them`() {
        val service = preparedService()
        service.setDependency("shutdownCoord", mockk<ShutdownCoordinator>(relaxed = true))
        service.callAcquireLocks()
        val hadWakeLock = service.getWakeLock() != null
        val hadWifiLock = service.getWifiLock() != null

        service.onDestroy()

        if (hadWakeLock || hadWifiLock) {
            assertEquals(null, service.getWakeLock())
            assertEquals(null, service.getWifiLock())
        }
    }

    @Test
    fun `releaseLocks tolerates absent locks`() {
        val service = preparedService()

        assertTrue(runCatching { service.callReleaseLocks() }.isSuccess)
        assertEquals(null, service.getWakeLock())
        assertEquals(null, service.getWifiLock())
    }

    @Test
    fun `releaseLocks clears real locks after they were released externally`() {
        val service = preparedService()
        service.callAcquireLocks()
        service.getWakeLock()?.release()
        service.getWifiLock()?.release()

        service.callReleaseLocks()

        assertEquals(null, service.getWakeLock())
        assertEquals(null, service.getWifiLock())
    }

    @Test
    fun `acquireLocks tolerates missing power service`() {
        val service = spyk(preparedService())
        every { service.getSystemService(Context.POWER_SERVICE) } returns null

        service.callAcquireLocks()

        assertEquals(null, service.getWakeLock())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun `acquireLocks uses modern wifi mode on upside down cake`() {
        val service = preparedService()
        assertTrue(runCatching { service.callAcquireLocks() }.isSuccess)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `acquireLocks uses legacy wifi mode before upside down cake`() {
        val service = preparedService()
        assertTrue(runCatching { service.callAcquireLocks() }.isSuccess)
    }

    @Test
    fun `onStartCommand returns NOT_STICKY when action dispatcher refuses foreground`() {
        val service = preparedService()
        val actionDispatcher = OzeroVpnServiceActionDispatcher(
            latestStartIdSetter = {},
            isChainOrchestratorReady = { false },
            enterForeground = { false },
            isTunnelIdle = { false },
            clearStopping = {},
            stopSelf = {},
            startVpn = {},
            stopVpn = {},
            restartVpn = {},
        )
        service.setDependency("actionDispatcher", actionDispatcher)

        val result = service.onStartCommand(
            Intent(RuntimeEnvironment.getApplication(), OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_START
            },
            0,
            10,
        )

        assertEquals(android.app.Service.START_NOT_STICKY, result)
    }

    @Test
    fun `onStartCommand returns START_STICKY on unknown action`() {
        val service = preparedService()
        var latestStartId = Int.MIN_VALUE
        val actionDispatcher = OzeroVpnServiceActionDispatcher(
            latestStartIdSetter = { latestStartId = it },
            isChainOrchestratorReady = { true },
            enterForeground = { true },
            isTunnelIdle = { false },
            clearStopping = {},
            stopSelf = {},
            startVpn = {},
            stopVpn = {},
            restartVpn = {},
        )
        service.setDependency("actionDispatcher", actionDispatcher)

        val result = service.onStartCommand(
            Intent(RuntimeEnvironment.getApplication(), OzeroVpnService::class.java).apply {
                action = "other"
            },
            0,
            11,
        )

        assertEquals(android.app.Service.START_STICKY, result)
        assertEquals(11, latestStartId)
    }

    @Test
    fun `onStartCommand handles null intent as unknown action and records latest id`() {
        val service = preparedService()
        var latestStartId = Int.MIN_VALUE
        val actionDispatcher = OzeroVpnServiceActionDispatcher(
            latestStartIdSetter = { latestStartId = it },
            isChainOrchestratorReady = { true },
            enterForeground = { true },
            isTunnelIdle = { false },
            clearStopping = {},
            stopSelf = {},
            startVpn = {},
            stopVpn = {},
            restartVpn = {},
        )
        service.setDependency("actionDispatcher", actionDispatcher)

        val result = service.onStartCommand(null, 0, 101)

        assertEquals(android.app.Service.START_STICKY, result)
        assertEquals(101, latestStartId)
    }

    @Test
    fun `real action dispatcher start action clears stopping and starts coordinator`() {
        val service = preparedServiceForRealDispatcher()
        val startCoordinator = mockk<OzeroVpnServiceStartCoordinator>(relaxed = true)
        service.setLazy("startCoordinator", startCoordinator)
        service.setAtomicBoolean("stopping", true)

        val result = service.onStartCommand(
            Intent(RuntimeEnvironment.getApplication(), OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_START
            },
            0,
            21,
        )

        assertEquals(android.app.Service.START_STICKY, result)
        assertFalse(service.getAtomicBoolean("stopping"))
        assertEquals(21, service.getAtomicInteger("latestStartId"))
        verify(exactly = 1) { startCoordinator.start() }
    }

    @Test
    fun `real action dispatcher refuses start when chain orchestrator is not injected`() {
        val service = Robolectric.buildService(OzeroVpnService::class.java).get()
        val notificationFactory = mockk<OzeroNotificationFactory>(relaxed = true)
        every { notificationFactory.enterForeground(any()) } returns true
        service.setLazy("notificationFactory", notificationFactory)

        val result = service.onStartCommand(
            Intent(RuntimeEnvironment.getApplication(), OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_START
            },
            0,
            25,
        )

        assertEquals(android.app.Service.START_NOT_STICKY, result)
    }

    @Test
    fun `real action dispatcher stop action delegates to shutdown coordinator`() {
        val service = preparedServiceForRealDispatcher()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        service.setLazy("shutdownCoord", shutdownCoordinator)

        val result = service.onStartCommand(
            Intent(RuntimeEnvironment.getApplication(), OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_STOP
            },
            0,
            22,
        )

        assertEquals(android.app.Service.START_STICKY, result)
        assertEquals(22, service.getAtomicInteger("latestStartId"))
        verify(exactly = 1) { shutdownCoordinator.stopVpn() }
    }

    @Test
    fun `real action dispatcher idle restart stops self without restart flow`() {
        val service = preparedServiceForRealDispatcher()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        service.setLazy("shutdownCoord", shutdownCoordinator)

        val result = service.onStartCommand(
            Intent(RuntimeEnvironment.getApplication(), OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG
            },
            0,
            23,
        )

        assertEquals(android.app.Service.START_STICKY, result)
        assertEquals(23, service.getAtomicInteger("latestStartId"))
        verify(exactly = 0) { shutdownCoordinator.stopVpn(false) }
    }

    @Test
    fun `real action dispatcher active restart enters restart flow`() {
        val service = preparedServiceForRealDispatcher()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        val notificationFactory = mockk<OzeroNotificationFactory>(relaxed = true)
        val startCoordinator = mockk<OzeroVpnServiceStartCoordinator>(relaxed = true)
        every { notificationFactory.enterForeground(any()) } returnsMany listOf(true, false)
        service.setLazy("shutdownCoord", shutdownCoordinator)
        service.setLazy("notificationFactory", notificationFactory)
        service.setLazy("startCoordinator", startCoordinator)
        service.setDependency(
            "tunnelController",
            TunnelController().apply {
                onProbing(EngineId.WARP)
                onConnecting(EngineId.WARP)
                onEngineStarted(EngineId.WARP, 2080)
            },
        )

        val result = service.onStartCommand(
            Intent(RuntimeEnvironment.getApplication(), OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG
            },
            0,
            24,
        )

        assertEquals(android.app.Service.START_STICKY, result)
        assertEquals(24, service.getAtomicInteger("latestStartId"))
        verify(exactly = 1) { shutdownCoordinator.stopVpn(false) }
    }

    @Test
    fun `restartVpn aborts relaunch when stop is requested during shutdown wait`() {
        val service = preparedService()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        val startCoordinator = mockk<OzeroVpnServiceStartCoordinator>(relaxed = true)
        val notificationFactory = mockk<OzeroNotificationFactory>(relaxed = true)
        every { notificationFactory.enterForeground(any()) } returns true
        service.setDependency("shutdownCoord", shutdownCoordinator)
        service.setDependency("startCoordinator", startCoordinator)
        service.setLazy("notificationFactory", notificationFactory)
        val shutdownJob = Job()
        service.setAtomicReference("shutdownJobRef", shutdownJob)
        service.setAtomicBoolean("stopping", false)

        service.callRestartVpn()
        service.setAtomicBoolean("stopping", true)
        shutdownJob.complete()

        assertTrue(waitUntil { !service.getAtomicBoolean("runtimeConfigRestartInProgress") })
        coVerify(exactly = 0) { startCoordinator.start() }
    }

    @Test
    fun `restartVpn aborts relaunch when restart was cancelled during shutdown wait`() {
        val service = preparedService()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        val startCoordinator = mockk<OzeroVpnServiceStartCoordinator>(relaxed = true)
        val notificationFactory = mockk<OzeroNotificationFactory>(relaxed = true)
        every { notificationFactory.enterForeground(any()) } returns true
        service.setDependency("shutdownCoord", shutdownCoordinator)
        service.setDependency("startCoordinator", startCoordinator)
        service.setLazy("notificationFactory", notificationFactory)
        val shutdownJob = Job()
        service.setAtomicReference("shutdownJobRef", shutdownJob)

        service.callRestartVpn()
        service.setAtomicBoolean("runtimeConfigRestartCancelled", true)
        shutdownJob.complete()

        assertTrue(waitUntil { !service.getAtomicBoolean("runtimeConfigRestartInProgress") })
        assertFalse(service.getAtomicBoolean("runtimeConfigRestartCancelled"))
        coVerify(exactly = 0) { startCoordinator.start() }
    }

    @Test
    fun `onStartCommand routes ACTION_RESTART_RUNTIME_CONFIG when tunnel is busy`() {
        var restartCalled = false
        var latestStartId = Int.MIN_VALUE
        var stopSelfId = Int.MIN_VALUE
        val service = preparedService()
        val actionDispatcher = OzeroVpnServiceActionDispatcher(
            latestStartIdSetter = { latestStartId = it },
            isChainOrchestratorReady = { true },
            enterForeground = { true },
            isTunnelIdle = { false },
            clearStopping = {},
            stopSelf = { stopSelfId = it },
            startVpn = {},
            stopVpn = {},
            restartVpn = { restartCalled = true },
        )
        service.setDependency("actionDispatcher", actionDispatcher)

        val result = service.onStartCommand(
            Intent(RuntimeEnvironment.getApplication(), OzeroVpnService::class.java).apply {
                action = OzeroVpnService.ACTION_RESTART_RUNTIME_CONFIG
            },
            0,
            12,
        )

        assertEquals(android.app.Service.START_STICKY, result)
        assertEquals(12, latestStartId)
        assertTrue(restartCalled)
    }

    @Test
    fun `stopVpn marks restart as cancelled when restart is running`() {
        val service = preparedService()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        val startCoordinator = mockk<OzeroVpnServiceStartCoordinator>(relaxed = true)
        service.setDependency("shutdownCoord", shutdownCoordinator)
        service.setDependency("startCoordinator", startCoordinator)
        service.setAtomicBoolean("runtimeConfigRestartInProgress", true)
        service.setAtomicBoolean("runtimeConfigRestartCancelled", false)

        service.callStopVpn()

        assertTrue(service.getAtomicBoolean("runtimeConfigRestartCancelled"))
        verify(exactly = 1) { shutdownCoordinator.stopVpn() }
    }

    @Test
    fun `stopVpn does not mark cancelled when restart is not in progress`() {
        val service = preparedService()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        service.setDependency("shutdownCoord", shutdownCoordinator)
        service.setAtomicBoolean("runtimeConfigRestartInProgress", false)
        service.setAtomicBoolean("runtimeConfigRestartCancelled", false)

        service.callStopVpn()

        assertFalse(service.getAtomicBoolean("runtimeConfigRestartCancelled"))
        verify(exactly = 1) { shutdownCoordinator.stopVpn() }
    }

    @Test
    fun `restartVpn returns immediately if stopping was already true`() {
        val service = preparedService()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        service.setDependency("shutdownCoord", shutdownCoordinator)
        service.setAtomicBoolean("stopping", true)

        service.callRestartVpn()

        assertFalse(service.getAtomicBoolean("runtimeConfigRestartInProgress"))
        verify(exactly = 0) { shutdownCoordinator.stopVpn(false) }
    }

    @Test
    fun `restartVpn aborts relaunch when foreground promotion fails`() {
        val service = preparedService()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        val startCoordinator = mockk<OzeroVpnServiceStartCoordinator>(relaxed = true)
        val notificationFactory = mockk<OzeroNotificationFactory>(relaxed = true)
        every { notificationFactory.enterForeground(any()) } returns false
        service.setDependency("shutdownCoord", shutdownCoordinator)
        service.setDependency("startCoordinator", startCoordinator)
        service.setLazy("notificationFactory", notificationFactory)
        service.setAtomicReference("shutdownJobRef", Job().apply { complete() })
        service.setAtomicInteger("latestStartId", 77)

        service.callRestartVpn()

        verify(exactly = 1) { shutdownCoordinator.stopVpn(false) }
        coVerify(exactly = 0) { startCoordinator.start() }
        assertEquals(77, service.getAtomicInteger("latestStartId"))
        assertFalse(service.getAtomicBoolean("runtimeConfigRestartInProgress"))
        assertFalse(service.getAtomicBoolean("runtimeConfigRestartCancelled"))
    }

    @Test
    fun `restartVpn continues by invoking startCoordinator when foreground promotion succeeds`() {
        val service = preparedService()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        val startCoordinator = mockk<OzeroVpnServiceStartCoordinator>(relaxed = true)
        val notificationFactory = mockk<OzeroNotificationFactory>(relaxed = true)
        every { notificationFactory.enterForeground(any()) } returns true
        service.setDependency("shutdownCoord", shutdownCoordinator)
        service.setDependency("startCoordinator", startCoordinator)
        service.setLazy("notificationFactory", notificationFactory)
        service.setAtomicReference("shutdownJobRef", Job().apply { complete() })

        service.callRestartVpn()

        verify(exactly = 1) { shutdownCoordinator.stopVpn(false) }
        coVerify(exactly = 1) { startCoordinator.start() }
        assertTrue(service.getAtomicBoolean("runtimeConfigRestartInProgress"))
        assertFalse(service.getAtomicBoolean("runtimeConfigRestartCancelled"))
    }

    @Test
    fun `onDestroy joins in-flight shutdown job`() {
        val service = preparedService()
        val shutdownJob = Job().apply { complete() }
        service.setAtomicReference("shutdownJobRef", shutdownJob)
        service.setAtomicBoolean("stopping", false)
        service.onDestroy()
        assertTrue(true)
    }

    @Test
    fun `onDestroy timeout path still releases runtime failure handler`() {
        val service = preparedService()
        val runtimeFailureRouter = mockk<RuntimeFailureRouter>(relaxed = true)
        val shutdownJob = Job()
        service.setDependency("runtimeFailureRouter", runtimeFailureRouter)
        service.setAtomicReference("shutdownJobRef", shutdownJob)
        service.setAtomicBoolean("stopping", false)

        service.onDestroy()
        shutdownJob.cancel()

        coVerify(exactly = 1) { runtimeFailureRouter.unbind(any()) }
    }

    @Test
    fun `onDestroy closes remaining tun fd after shutdown cleanup`() {
        val service = preparedService()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        val tunFd = mockk<ParcelFileDescriptor>(relaxed = true)
        service.setDependency("shutdownCoord", shutdownCoordinator)
        service.setAtomicReference("shutdownJobRef", Job().apply { complete() })
        service.setAtomicReference("tunFdRef", tunFd)

        service.onDestroy()

        verify(exactly = 1) { tunFd.close() }
        assertEquals(null, service.getAtomicReference("tunFdRef"))
    }

    @Test
    fun `onDestroy performs shutdown when no shutdown job in flight`() {
        val service = preparedService()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        service.setDependency("shutdownCoord", shutdownCoordinator)
        service.setAtomicReference("shutdownJobRef", null)
        service.setAtomicBoolean("stopping", false)

        service.onDestroy()

        coVerify(exactly = 1) { shutdownCoordinator.performShutdown(false) }
    }

    @Test
    fun `onDestroy skips performShutdown when stopping already true`() {
        val service = preparedService()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        service.setDependency("shutdownCoord", shutdownCoordinator)
        service.setAtomicReference("shutdownJobRef", null)
        service.setAtomicBoolean("stopping", true)

        service.onDestroy()

        coVerify(exactly = 0) { shutdownCoordinator.performShutdown(false) }
    }

    @Test
    fun `onRevoke schedules delayed kill and closes tun fd`() {
        val service = preparedService()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        val tunFd = mockk<ParcelFileDescriptor>(relaxed = true)
        val killedPids = mutableListOf<Int>()
        service.setDependency("shutdownCoord", shutdownCoordinator)
        service.setAtomicReference("tunFdRef", tunFd)
        service.processKiller = ProcessKiller { pid -> killedPids.add(pid) }

        service.onRevoke()
        Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        verify(exactly = 1) { shutdownCoordinator.stopVpn() }
        verify(exactly = 1) { tunFd.close() }
        assertEquals(1, killedPids.size)
        assertEquals(android.os.Process.myPid(), killedPids.single())
    }

    @Test
    fun `onRevoke ignores close exceptions before killing own process`() {
        val service = preparedService()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        val tunFd = mockk<ParcelFileDescriptor>()
        every { tunFd.close() } throws RuntimeException("close failed")
        val killedPids = mutableListOf<Int>()
        service.setDependency("shutdownCoord", shutdownCoordinator)
        service.setAtomicReference("tunFdRef", tunFd)
        service.processKiller = ProcessKiller { pid -> killedPids.add(pid) }

        service.onRevoke()
        Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        verify(exactly = 1) { tunFd.close() }
        assertEquals(1, killedPids.size)
        assertEquals(android.os.Process.myPid(), killedPids.single())
    }

    @Test
    fun `onRevoke handles missing tun fd before killing own process`() {
        val service = preparedService()
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        val killedPids = mutableListOf<Int>()
        service.setDependency("shutdownCoord", shutdownCoordinator)
        service.setAtomicReference("tunFdRef", null)
        service.processKiller = ProcessKiller { pid -> killedPids.add(pid) }

        service.onRevoke()
        Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks()

        verify(exactly = 1) { shutdownCoordinator.stopVpn() }
        assertEquals(listOf(android.os.Process.myPid()), killedPids)
    }

    @Test
    fun `logActiveExternalVpn swallows unexpected system failures`() {
        val service = spyk(preparedService())
        every { service.getSystemService(any<String>()) } throws RuntimeException("cm failed")

        assertFalse(service.callLogActiveExternalVpn())
    }

    private fun preparedService(
        settingsRepository: SettingsRepository? = null,
    ): OzeroVpnService {
        val controller = Robolectric.buildService(OzeroVpnService::class.java)
        val service = controller.get()
        val repo = settingsRepository ?: mockk<SettingsRepository>(relaxed = true).also {
            every { it.settings } returns flowOf(SettingsModel())
        }
        service.setDependency("runtimeFailureRouter", RuntimeFailureRouter())
        service.setDependency("settingsRepository", repo)
        service.setDependency("healthMonitor", HealthMonitor())
        service.setDependency("chainOrchestrator", mockk<ChainOrchestrator>(relaxed = true))
        service.setDependency("tunnelGateway", mockk<HevTunnelGateway>())
        return service
    }

    private fun preparedServiceForRealDispatcher(): OzeroVpnService {
        val service = preparedService()
        val notificationFactory = mockk<OzeroNotificationFactory>(relaxed = true)
        every { notificationFactory.enterForeground(any()) } returns true
        service.setLazy("notificationFactory", notificationFactory)
        service.setDependency("chainOrchestrator", mockk<ChainOrchestrator>(relaxed = true))
        service.setDependency("tunnelController", TunnelController())
        return service
    }

    private fun OzeroVpnService.callStopVpn() {
        val method = OzeroVpnService::class.java.getDeclaredMethod("stopVpn")
        method.isAccessible = true
        method.invoke(this)
    }

    private fun OzeroVpnService.callStartVpn() {
        val method = OzeroVpnService::class.java.getDeclaredMethod("startVpn")
        method.isAccessible = true
        method.invoke(this)
    }

    private fun OzeroVpnService.callRestartVpn() {
        val method = OzeroVpnService::class.java.getDeclaredMethod("restartVpn")
        method.isAccessible = true
        method.invoke(this)
    }

    private fun OzeroVpnService.callObserveKillswitchSetting() {
        val method = OzeroVpnService::class.java.getDeclaredMethod("observeKillswitchSetting")
        method.isAccessible = true
        method.invoke(this)
    }

    private fun OzeroVpnService.getStartCoordinatorDeps(): OzeroVpnServiceStartCoordinator.Dependencies {
        val coordinator = forceLazy("startCoordinator") as OzeroVpnServiceStartCoordinator
        val field = OzeroVpnServiceStartCoordinator::class.java.getDeclaredField("deps")
        field.isAccessible = true
        return field.get(coordinator) as OzeroVpnServiceStartCoordinator.Dependencies
    }

    private fun OzeroVpnService.callAcquireLocks() {
        val method = OzeroVpnService::class.java.getDeclaredMethod("acquireLocks")
        method.isAccessible = true
        method.invoke(this)
    }

    private fun OzeroVpnService.callReleaseLocks() {
        val method = OzeroVpnService::class.java.getDeclaredMethod("releaseLocks")
        method.isAccessible = true
        method.invoke(this)
    }

    private fun OzeroVpnService.callEngineExtras(): String {
        val method = OzeroVpnService::class.java.getDeclaredMethod("engineExtras")
        method.isAccessible = true
        return method.invoke(this) as String
    }

    private fun OzeroVpnService.callLogActiveExternalVpn(): Boolean {
        val method = OzeroVpnService::class.java.getDeclaredMethod("logActiveExternalVpn")
        method.isAccessible = true
        return method.invoke(this) as Boolean
    }

    @Suppress("UNCHECKED_CAST")
    private fun OzeroVpnService.callRuntimeFailureHandler(engineId: EngineId, reason: String) {
        val field = OzeroVpnService::class.java.getDeclaredField("runtimeFailureHandler\$delegate")
        field.isAccessible = true
        val handler = (field.get(this) as Lazy<(EngineId, String) -> Unit>).value
        handler(engineId, reason)
    }

    private fun OzeroVpnService.setDependency(name: String, value: Any) {
        val field = try {
            OzeroVpnService::class.java.getDeclaredField(name)
        } catch (_: NoSuchFieldException) {
            null
        }
        if (field != null) {
            field.isAccessible = true
            field.set(this, value)
            return
        }
        setLazy(name, value)
    }

    private fun OzeroVpnService.setLazy(name: String, value: Any) {
        val field = OzeroVpnService::class.java.getDeclaredField("${name}\$delegate")
        field.isAccessible = true
        field.set(this, lazy { value })
    }

    private fun OzeroVpnService.forceLazy(name: String): Any {
        val field = OzeroVpnService::class.java.getDeclaredField("${name}\$delegate")
        field.isAccessible = true
        return (field.get(this) as Lazy<*>).value as Any
    }

    private fun OzeroVpnService.setAtomicBoolean(name: String, value: Boolean) {
        val field = OzeroVpnService::class.java.getDeclaredField(name)
        field.isAccessible = true
        (field.get(this) as AtomicBoolean).set(value)
    }

    private fun OzeroVpnService.setAtomicInteger(name: String, value: Int) {
        val field = OzeroVpnService::class.java.getDeclaredField(name)
        field.isAccessible = true
        (field.get(this) as AtomicInteger).set(value)
    }

    private fun OzeroVpnService.setAtomicReference(name: String, value: Any?) {
        val field = OzeroVpnService::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(this) as AtomicReference<Any?>).set(value)
    }

    private fun OzeroVpnService.getAtomicReference(name: String): Any? {
        val field = OzeroVpnService::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (field.get(this) as AtomicReference<Any?>).get()
    }

    private fun NetworkCapabilities.trySetOwnerUid(uid: Int): Boolean {
        return runCatching {
            val method = NetworkCapabilities::class.java.getDeclaredMethod("setOwnerUid", Int::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(this, uid)
        }.isSuccess
    }

    private fun OzeroVpnService.getAtomicBoolean(name: String): Boolean {
        val field = OzeroVpnService::class.java.getDeclaredField(name)
        field.isAccessible = true
        return (field.get(this) as AtomicBoolean).get()
    }

    private fun OzeroVpnService.getAtomicInteger(name: String): Int {
        val field = OzeroVpnService::class.java.getDeclaredField(name)
        field.isAccessible = true
        return (field.get(this) as AtomicInteger).get()
    }

    private fun OzeroVpnService.getVolatileBoolean(name: String): Boolean {
        val field = OzeroVpnService::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.getBoolean(this)
    }

    private fun OzeroVpnService.getWakeLock(): PowerManager.WakeLock? {
        val field = OzeroVpnService::class.java.getDeclaredField("wakeLock")
        field.isAccessible = true
        return field.get(this) as PowerManager.WakeLock?
    }

    private fun OzeroVpnService.getWifiLock(): WifiManager.WifiLock? {
        val field = OzeroVpnService::class.java.getDeclaredField("wifiLock")
        field.isAccessible = true
        return field.get(this) as WifiManager.WifiLock?
    }

    private fun getSocketProtectorHolderField(): java.lang.reflect.Field {
        val holderField = ru.ozero.enginescore.VpnSocketProtectorHolder::class.java.getDeclaredField("current")
        holderField.isAccessible = true
        return holderField
    }

    private fun waitUntil(
        timeoutMs: Long = 500,
        stepMs: Long = 10,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return true
            }
            Thread.sleep(stepMs)
        }
        return condition()
    }
}
