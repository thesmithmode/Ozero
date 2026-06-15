package ru.ozero.commonvpn

import android.content.Intent
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SettingsRepository
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OzeroVpnServiceBranchCoverageTest {

    @Test
    fun `onStartCommand propagates action dispatcher runtime exception`() {
        val service = preparedService()
        val actionDispatcher = mockk<OzeroVpnServiceActionDispatcher>(relaxed = true)
        every { actionDispatcher.dispatch(any(), any()) } answers {
            throw IllegalStateException("dispatcher failed")
        }
        service.setDependency("actionDispatcher", actionDispatcher)

        assertFailsWith<IllegalStateException> {
            service.onStartCommand(
                Intent(RuntimeEnvironment.getApplication(), OzeroVpnService::class.java).apply {
                    action = OzeroVpnService.ACTION_START
                },
                0,
                1,
            )
        }
    }

    @Test
    fun `onDestroy joins provided shutdown job and skips performShutdown path`() {
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        val service = preparedService()
            .also { it.setDependency("shutdownCoord", shutdownCoordinator) }
            .also { it.setAtomicReference("shutdownJobRef", Job().apply { complete() }) }

        service.onDestroy()

        coVerify(exactly = 0) { shutdownCoordinator.performShutdown(any()) }
        verify(exactly = 0) { shutdownCoordinator.stopVpn(false) }
    }

    @Test
    fun `restartVpn with no shutdown job in flight still tries to restart`() {
        val notificationFactory = mockk<OzeroNotificationFactory>(relaxed = true)
        val restartStarted = AtomicBoolean(false)
        val startCoordinator = mockk<OzeroVpnServiceStartCoordinator>(relaxed = true)
        val shutdownCoordinator = mockk<ShutdownCoordinator>(relaxed = true)
        val service = preparedService()
            .also { it.setDependency("notificationFactory", notificationFactory) }
            .also { it.setDependency("startCoordinator", startCoordinator) }
            .also { it.setDependency("shutdownCoord", shutdownCoordinator) }
            .also { it.setAtomicReference("shutdownJobRef", null) }

        every { startCoordinator.start() } answers {
            restartStarted.set(true)
        }
        every { notificationFactory.enterForeground(service) } answers {
            true
        }

        service.callRestartVpn()

        assertTrue(waitUntil { restartStarted.get() })
        verify(exactly = 1) { startCoordinator.start() }
        verify(exactly = 1) { shutdownCoordinator.stopVpn(false) }
    }

    private fun preparedService(
        runtimeFailureRouter: RuntimeFailureRouter = RuntimeFailureRouter(),
        settingsRepository: SettingsRepository? = null,
    ): OzeroVpnService {
        val service = Robolectric.buildService(OzeroVpnService::class.java).get()
        val repo = settingsRepository ?: mockk<SettingsRepository>(relaxed = true).also {
            every { it.settings } returns flowOf(SettingsModel())
        }
        service.setDependency("runtimeFailureRouter", runtimeFailureRouter)
        service.setDependency("settingsRepository", repo)
        service.setDependency("healthMonitor", HealthMonitor())
        service.setDependency("chainOrchestrator", mockk<ChainOrchestrator>(relaxed = true))
        service.setDependency("tunnelGateway", mockk<HevTunnelGateway>())
        service.setDependency("tunnelController", TunnelController())
        service.setDependency("sessionStatsRecorder", SessionStatsRecorder.NoOp)
        service.setDependency("splitTunnelRulesProvider", SplitTunnelRulesProvider.NoOp)
        service.setDependency("enginePlugins", emptySet<EnginePlugin>())
        return service
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

    private fun OzeroVpnService.setAtomicReference(name: String, value: Any?) {
        val field = OzeroVpnService::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(this) as AtomicReference<Any?>).set(value)
    }

    private fun OzeroVpnService.callRestartVpn() {
        val method = OzeroVpnService::class.java.getDeclaredMethod("restartVpn")
        method.isAccessible = true
        method.invoke(this)
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
