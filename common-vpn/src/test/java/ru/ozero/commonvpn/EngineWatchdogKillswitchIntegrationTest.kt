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
    fun `startup lockdown keeps killswitch active without stopVpn`() =
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
                "startup lockdown fd РґРѕР»Р¶РµРЅ СЃС‡РёС‚Р°С‚СЊСЃСЏ blocking TUN, РёРЅР°С‡Рµ fail-closed С‚РµСЂСЏРµС‚СЃСЏ РґРѕ РѕСЃРЅРѕРІРЅРѕРіРѕ tunFd.",
            )
            assertEquals(
                0,
                stopVpnCount.get(),
                "РџСЂРё Р¶РёРІРѕРј startup lockdown fd РЅРµР»СЊР·СЏ РІС‹Р·С‹РІР°С‚СЊ stopVpnRequest: СЌС‚Рѕ СЃРЅРёРјР°РµС‚ Р±Р»РѕРєРёСЂРѕРІРєСѓ С‚СЂР°С„РёРєР°.",
            )
        }

    @Test
    fun `runtime restart without blocking fd falls back to stopVpn`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = TunnelController()
            controller.onProbing(EngineId.WARP)
            controller.onConnecting(EngineId.WARP)

            val stopVpnCount = AtomicReference(0)
            val watchdog = buildWatchdog(
                controller = controller,
                scope = CoroutineScope(UnconfinedTestDispatcher() + SupervisorJob()),
                killswitch = true,
                tunFd = null,
                stopVpnInvocations = stopVpnCount,
            )

            watchdog.handleEngineFailure(EngineId.WARP, "runtime restart gap")

            assertFalse(controller.killswitchActive.value)
            assertEquals(1, stopVpnCount.get())
            assertIs<TunnelState.Failed>(controller.state.value)
        }

    @Test
    fun `fd alive keeps killswitch active and failed state`() =
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
                "killswitchActive must stay true after enterKillswitchMode.",
            )
            val state = controller.state.value
            assertIs<TunnelState.Failed>(state, "Tunnel state РѕР±СЏР·Р°РЅ РїРµСЂРµР№С‚Рё РІ Failed.")
            assertEquals(EngineId.WARP, state.engineId)
            assertTrue(
                state.reason.contains("remote-binder-died"),
                "Failed.reason РѕР±СЏР·Р°РЅ СЃРѕРґРµСЂР¶Р°С‚СЊ РїРµСЂРµРґР°РЅРЅС‹Р№ reason. Got: ${state.reason}",
            )
            assertEquals(
                0,
                stopVpnCount.get(),
                "stopVpnRequest must stay silent while killswitch=true.",
            )
        }

    @Test
    fun `stopping state prevents re-entering shutdown`() =
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
                "killswitchActive РѕР±СЏР·Р°РЅ РѕСЃС‚Р°С‚СЊСЃСЏ false РїСЂРё С€С‚Р°С‚РЅРѕР№ РѕСЃС‚Р°РЅРѕРІРєРµ, РґР°Р¶Рµ РµСЃР»Рё fd РµС‰С‘ Р¶РёРІРѕР№.",
            )
            assertIs<TunnelState.Disconnecting>(
                controller.state.value,
                "Engine failure РІРѕ РІСЂРµРјСЏ stopping РЅРµ РґРѕР»Р¶РµРЅ РїСЂРµРІСЂР°С‰Р°С‚СЊ Disconnecting РІ Failed.",
            )
            assertEquals(
                0,
                stopVpnCount.get(),
                "Р’Рѕ РІСЂРµРјСЏ stopping watchdog РЅРµ РґРѕР»Р¶РµРЅ РїРѕРІС‚РѕСЂРЅРѕ РІС…РѕРґРёС‚СЊ РІ stopVpnRequest.",
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
    fun `handleEngineFailure killswitch=false вЂ” observer РІРёРґРёС‚ Failed РЅРѕ РќР• killswitchActive`() =
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
                "killswitchActive РѕР±СЏР·Р°РЅ РѕСЃС‚Р°С‚СЊСЃСЏ false РїСЂРё killswitch=off вЂ” graceful shutdown branch.",
            )
            assertIs<TunnelState.Failed>(
                controller.state.value,
                "Failed state РѕР±СЏР·Р°РЅ РІС‹СЃС‚Р°РІРёС‚СЊСЃСЏ С‡РµСЂРµР· onEngineDied.",
            )
            assertEquals(
                1,
                stopVpnCount.get(),
                "stopVpnRequest РѕР±СЏР·Р°РЅ РІС‹Р·РІР°С‚СЊСЃСЏ СЂРѕРІРЅРѕ РѕРґРёРЅ СЂР°Р· вЂ” graceful VPN shutdown.",
            )
        }

    @Test
    fun `handleEngineFailure killswitch=true + fdAlive=null вЂ” fallback РЅР° stopVpnRequest, РЅРµ lockdown`() =
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
                "killswitchActive РѕР±СЏР·Р°РЅ РѕСЃС‚Р°С‚СЊСЃСЏ false РєРѕРіРґР° fdAlive=null вЂ” РЅРµС‚ TUN РґР»СЏ lockdown.",
            )
            assertEquals(
                1,
                stopVpnCount.get(),
                "stopVpnRequest РѕР±СЏР·Р°РЅ РІС‹Р·РІР°С‚СЊСЃСЏ РєРѕРіРґР° РЅРµС‚ fdAlive вЂ” VPN service РЅСѓР¶РЅРѕ РѕСЃС‚Р°РЅРѕРІРёС‚СЊ.",
            )
        }

    @Test
    fun `onKillswitchReleased СЃР±СЂР°СЃС‹РІР°РµС‚ killswitchActive вЂ” UI РјРѕР¶РµС‚ РІРµСЂРЅСѓС‚СЊСЃСЏ РІ Idle`() =
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
                "onKillswitchReleased РѕР±СЏР·Р°РЅ СЃР±СЂРѕСЃРёС‚СЊ killswitchActive вЂ” РёРЅР°С‡Рµ UI Р±Р»РѕРєРёСЂСѓРµС‚СЃСЏ РЅР°РІСЃРµРіРґР°.",
            )
        }
}
