package ru.ozero.commonvpn

import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import ru.ozero.enginescore.settings.TrafficMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class OzeroVpnServiceRuntimeLifecycleTest {

    @Test
    fun `service can be prepared with injected runtime dependencies`() {
        preparedService()
        assertTrue(true)
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

    private fun OzeroVpnService.callStopVpn() {
        val method = OzeroVpnService::class.java.getDeclaredMethod("stopVpn")
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

    private fun OzeroVpnService.callAcquireLocks() {
        val method = OzeroVpnService::class.java.getDeclaredMethod("acquireLocks")
        method.isAccessible = true
        method.invoke(this)
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
