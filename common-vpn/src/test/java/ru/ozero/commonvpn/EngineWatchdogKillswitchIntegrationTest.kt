package ru.ozero.commonvpn

import android.os.ParcelFileDescriptor
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.ChainOrchestrator
import ru.ozero.enginescore.EngineId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EngineWatchdogKillswitchIntegrationTest {

    private fun buildWatchdog(
        controller: TunnelController,
        scope: CoroutineScope,
        killswitch: Boolean,
        tunFd: ParcelFileDescriptor?,
        lockdownStartupFd: ParcelFileDescriptor? = null,
        stopping: Boolean = false,
        stopVpnInvocations: AtomicReference<Int>,
    ): EngineWatchdogCoordinator {
        val healthMonitor = HealthMonitor()
        val chainOrchestrator = mockk<ChainOrchestrator>(relaxed = true)
        val notificationFactory = mockk<OzeroNotificationFactory>(relaxed = true)
        val tunFdRef = AtomicReference<ParcelFileDescriptor?>(tunFd)
        val statsJobRef = AtomicReference<Job?>(null)
        val stopping = AtomicBoolean(stopping)
        val starting = AtomicBoolean(false)
        return EngineWatchdogCoordinator(
            scope = scope,
            healthMonitor = healthMonitor,
            enginePlugins = emptySet(),
            tunnelController = controller,
            chainOrchestrator = chainOrchestrator,
            notificationFactory = notificationFactory,
            tunFdRef = tunFdRef,
            lockdownStartupFdRef = AtomicReference(lockdownStartupFd),
            statsJobRef = statsJobRef,
            stopping = stopping,
            starting = starting,
            killswitchProvider = { killswitch },
            stopVpnRequest = {
                stopVpnInvocations.updateAndGet { it + 1 }
            },
        )
    }

    @Test
    fun `handleEngineFailure killswitch=true + startupLockdownFdAlive — observer видит killswitchActive без stopVpn`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = TunnelController()
            controller.onProbing(EngineId.WARP)
            controller.onConnecting(EngineId.WARP)

            val stopVpnCount = AtomicReference(0)
            val lockdownFd = mockk<ParcelFileDescriptor>(relaxed = true)
            val watchdog = buildWatchdog(
                controller = controller,
                scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
                killswitch = true,
                tunFd = null,
                lockdownStartupFd = lockdownFd,
                stopVpnInvocations = stopVpnCount,
            )

            watchdog.handleEngineFailure(EngineId.WARP, "startup failed")

            assertTrue(
                controller.killswitchActive.value,
                "startup lockdown fd должен считаться blocking TUN, иначе fail-closed теряется до основного tunFd.",
            )
            assertEquals(
                0,
                stopVpnCount.get(),
                "При живом startup lockdown fd нельзя вызывать stopVpnRequest: это снимает блокировку трафика.",
            )
        }

    @Test
    fun `handleEngineFailure killswitch=true + fdAlive — observer видит killswitchActive=true и Failed state`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = TunnelController()
            controller.onProbing(EngineId.WARP)
            controller.onConnecting(EngineId.WARP)
            controller.onEngineStarted(EngineId.WARP, socksPort = 0)
            assertIs<TunnelState.Connected>(controller.state.value)
            assertFalse(controller.killswitchActive.value)

            val stopVpnCount = AtomicReference(0)
            val fakeFd = mockk<ParcelFileDescriptor>(relaxed = true)
            val watchdog = buildWatchdog(
                controller = controller,
                scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
                killswitch = true,
                tunFd = fakeFd,
                stopVpnInvocations = stopVpnCount,
            )

            watchdog.handleEngineFailure(EngineId.WARP, "remote-binder-died")

            assertTrue(
                controller.killswitchActive.value,
                "killswitchActive обязан стать true после enterKillswitchMode — иначе UI не знает " +
                    "что трафик заблокирован.",
            )
            val state = controller.state.value
            assertIs<TunnelState.Failed>(state, "Tunnel state обязан перейти в Failed.")
            assertEquals(EngineId.WARP, state.engineId)
            assertTrue(
                state.reason.contains("remote-binder-died"),
                "Failed.reason обязан содержать переданный reason. Got: ${state.reason}",
            )
            assertEquals(
                0,
                stopVpnCount.get(),
                "stopVpnRequest НЕ должен вызываться при killswitch=true — chain должен остаться " +
                    "остановленным но VPN service сохраняется для UI lockdown notification.",
            )
        }

    @Test
    fun `handleEngineFailure killswitch=true + fdAlive + stopping=true — не re-enter shutdown`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = TunnelController()
            controller.onProbing(EngineId.WARP)
            controller.onConnecting(EngineId.WARP)
            controller.onEngineStarted(EngineId.WARP, socksPort = 0)
            controller.onDisconnecting()

            val stopVpnCount = AtomicReference(0)
            val fakeFd = mockk<ParcelFileDescriptor>(relaxed = true)
            val watchdog = buildWatchdog(
                controller = controller,
                scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
                killswitch = true,
                tunFd = fakeFd,
                stopping = true,
                stopVpnInvocations = stopVpnCount,
            )

            watchdog.handleEngineFailure(EngineId.WARP, "remote-binder-died")

            assertFalse(
                controller.killswitchActive.value,
                "killswitchActive обязан остаться false при штатной остановке, даже если fd ещё живой.",
            )
            assertIs<TunnelState.Disconnecting>(
                controller.state.value,
                "Engine failure во время stopping не должен превращать Disconnecting в Failed.",
            )
            assertEquals(
                0,
                stopVpnCount.get(),
                "Во время stopping watchdog не должен повторно входить в stopVpnRequest.",
            )
        }

    @Test
    fun `handleEngineFailure ignores inactive sidecar engine without killswitch or stop`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = TunnelController()
            controller.onProbing(EngineId.WARP)
            controller.onConnecting(EngineId.WARP)
            controller.onEngineStarted(EngineId.WARP, socksPort = 0)

            val stopVpnCount = AtomicReference(0)
            val fakeFd = mockk<ParcelFileDescriptor>(relaxed = true)
            val watchdog = buildWatchdog(
                controller = controller,
                scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
                killswitch = true,
                tunFd = fakeFd,
                stopVpnInvocations = stopVpnCount,
            )

            watchdog.handleEngineFailure(EngineId.URNETWORK, "relay io-loop-ended")

            assertFalse(controller.killswitchActive.value)
            assertEquals(0, stopVpnCount.get())
            val state = controller.state.value
            assertIs<TunnelState.Connected>(state)
            assertEquals(EngineId.WARP, state.engineId)
        }

    @Test
    fun `handleEngineFailure killswitch=false — observer видит Failed но НЕ killswitchActive`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = TunnelController()
            controller.onProbing(EngineId.BYEDPI)
            controller.onConnecting(EngineId.BYEDPI)
            controller.onEngineStarted(EngineId.BYEDPI, socksPort = 1080)

            val stopVpnCount = AtomicReference(0)
            val fakeFd = mockk<ParcelFileDescriptor>(relaxed = true)
            val watchdog = buildWatchdog(
                controller = controller,
                scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
                killswitch = false,
                tunFd = fakeFd,
                stopVpnInvocations = stopVpnCount,
            )

            watchdog.handleEngineFailure(EngineId.BYEDPI, "engine died")

            assertFalse(
                controller.killswitchActive.value,
                "killswitchActive обязан остаться false при killswitch=off — graceful shutdown branch.",
            )
            assertIs<TunnelState.Failed>(
                controller.state.value,
                "Failed state обязан выставиться через onEngineDied.",
            )
            assertEquals(
                1,
                stopVpnCount.get(),
                "stopVpnRequest обязан вызваться ровно один раз — graceful VPN shutdown.",
            )
        }

    @Test
    fun `handleEngineFailure killswitch=true + fdAlive=null — fallback на stopVpnRequest, не lockdown`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = TunnelController()
            controller.onProbing(EngineId.URNETWORK)
            controller.onConnecting(EngineId.URNETWORK)
            controller.onEngineStarted(EngineId.URNETWORK, socksPort = 0)

            val stopVpnCount = AtomicReference(0)
            val watchdog = buildWatchdog(
                controller = controller,
                scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
                killswitch = true,
                tunFd = null,
                stopVpnInvocations = stopVpnCount,
            )

            watchdog.handleEngineFailure(EngineId.URNETWORK, "io-loop-ended")

            assertFalse(
                controller.killswitchActive.value,
                "killswitchActive обязан остаться false когда fdAlive=null — нет TUN для lockdown.",
            )
            assertEquals(
                1,
                stopVpnCount.get(),
                "stopVpnRequest обязан вызваться когда нет fdAlive — VPN service нужно остановить.",
            )
        }

    @Test
    fun `onKillswitchReleased сбрасывает killswitchActive — UI может вернуться в Idle`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = TunnelController()
            controller.onProbing(EngineId.WARP)
            controller.onConnecting(EngineId.WARP)
            controller.onEngineStarted(EngineId.WARP, socksPort = 0)

            val stopVpnCount = AtomicReference(0)
            val fakeFd = mockk<ParcelFileDescriptor>(relaxed = true)
            val watchdog = buildWatchdog(
                controller = controller,
                scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
                killswitch = true,
                tunFd = fakeFd,
                stopVpnInvocations = stopVpnCount,
            )

            watchdog.handleEngineFailure(EngineId.WARP, "binder-died")
            assertTrue(controller.killswitchActive.value)

            controller.onKillswitchReleased()

            assertFalse(
                controller.killswitchActive.value,
                "onKillswitchReleased обязан сбросить killswitchActive — иначе UI блокируется навсегда.",
            )
        }
}
