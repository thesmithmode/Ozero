package ru.ozero.app.vpn

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.TrafficMode

@OptIn(ExperimentalCoroutinesApi::class)
class EngineSettingsRestartObserverHandleStartupStateTest : EngineSettingsRestartObserverTestBase() {

    @Test
    fun `handle restart engine mismatch in Probing Connecting — race fix`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Idle)
        val restarts = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val observer = EngineSettingsRestartObserver(
            settingsFlow = flow,
            vpnStateProvider = { state.value },
            onRestartConnected = { restarts += it },
        )

        val snapshot = EngineSettingsRestartObserver.Snapshot(
            manualEngine = EngineId.WARP,
            byedpiWinningArgs = null,
            ipv6Enabled = false,
            trafficMode = TrafficMode.TUN,
            customDnsServers = emptyList(),
            engineAutoPriority = null,
        )

        observer.handle(trigger(snapshot, snapshot))
        assertTrue(restarts.isEmpty(), "Idle: restart skip")

        state.value = TunnelState.Disconnecting
        observer.handle(trigger(snapshot, snapshot))
        assertTrue(restarts.isEmpty(), "Disconnecting: restart skip")

        state.value = TunnelState.Failed(EngineId.BYEDPI, "x")
        observer.handle(trigger(snapshot, snapshot))
        assertTrue(restarts.isEmpty(), "Failed: restart skip")

        state.value = TunnelState.Probing(EngineId.URNETWORK)
        observer.handle(trigger(snapshot, snapshot))
        assertEquals(
            1,
            restarts.size,
            "Probing(URNETWORK) + snapshot=WARP → mismatch → restart обязан фаерить. " +
                "Регрессия 2026-05-20: юзер тапает chip URNETWORK→WARP во время Probing/Connecting, " +
                "debounce 4s проглатывает change пока state ≠ Connected, " +
                "после Connected восстановления триггера нет → chip=WARP но engine=URNETWORK.",
        )

        state.value = TunnelState.Connecting(EngineId.URNETWORK)
        observer.handle(trigger(snapshot, snapshot))
        assertEquals(2, restarts.size, "Connecting(URNETWORK) + snapshot=WARP → mismatch → restart")

        state.value = TunnelState.Connected(EngineId.URNETWORK, 1080)
        observer.handle(trigger(snapshot, snapshot))
        assertEquals(3, restarts.size, "Connected: restart всегда фаерит (backwards-compat)")
    }

    @Test
    fun `handle skip — engine matches target во время Probing Connecting`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Idle)
        val restarts = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val observer = EngineSettingsRestartObserver(
            settingsFlow = flow,
            vpnStateProvider = { state.value },
            onRestartConnected = { restarts += it },
        )
        val snapshot = EngineSettingsRestartObserver.Snapshot(
            manualEngine = EngineId.WARP,
            byedpiWinningArgs = null,
            ipv6Enabled = false,
            trafficMode = TrafficMode.TUN,
            customDnsServers = emptyList(),
            engineAutoPriority = null,
        )

        state.value = TunnelState.Probing(EngineId.WARP)
        observer.handle(
            trigger(previous = snapshot.copy(manualEngine = EngineId.BYEDPI), snapshot = snapshot),
        )
        assertTrue(restarts.isEmpty(), "Probing(WARP) + snapshot=WARP → skip")

        state.value = TunnelState.Connecting(EngineId.WARP)
        observer.handle(trigger(snapshot, snapshot))
        assertTrue(restarts.isEmpty(), "Connecting(WARP) + snapshot=WARP → skip")

        state.value = TunnelState.Probing(null)
        observer.handle(trigger(snapshot, snapshot))
        assertTrue(
            restarts.isEmpty(),
            "Probing(null) — pre-permission, VPN service сам подхватит latest manualEngine при выходе. " +
                "Здесь нечего рестартить.",
        )

        state.value = TunnelState.Connected(EngineId.WARP, 1080)
        observer.handle(trigger(snapshot, snapshot))
        assertEquals(
            0,
            restarts.size,
            "Connected(WARP) + snapshot=WARP → restart skip. Late DataStore emissions after start " +
                "must not tear down a healthy tunnel; settings apply on next connect unless engine target changes.",
        )
    }

    @Test
    fun `handle skip — late settings snapshot не рестартит stable connected same engine`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Probing(EngineId.FPTN))
        val restarts = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val observer = EngineSettingsRestartObserver(
            settingsFlow = flow,
            vpnStateProvider = { state.value },
            onRestartConnected = { restarts += it },
        )
        val snapshot = EngineSettingsRestartObserver.Snapshot(
            manualEngine = EngineId.FPTN,
            byedpiWinningArgs = "--changed",
            ipv6Enabled = false,
            trafficMode = TrafficMode.TUN,
            customDnsServers = listOf("45.90.28.168", "45.90.30.168"),
            engineAutoPriority = null,
        )

        observer.handle(
            trigger(previous = snapshot.copy(manualEngine = EngineId.BYEDPI), snapshot = snapshot),
        )
        state.value = TunnelState.Connected(EngineId.FPTN, 0)
        observer.handle(trigger(snapshot, snapshot))

        assertTrue(
            restarts.isEmpty(),
            "Regression v1.0.14: a delayed settings emission while FPTN was already Connected caused " +
                "MainActivity to stop/start VPN and amplified native runtime instability.",
        )
    }

    @Test
    fun `handle restart — second same-engine startup snapshot is a real settings change`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Probing(EngineId.FPTN))
        val restarts = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val observer = EngineSettingsRestartObserver(
            settingsFlow = flow,
            vpnStateProvider = { state.value },
            onRestartConnected = { restarts += it },
        )
        val initial = EngineSettingsRestartObserver.Snapshot(
            manualEngine = EngineId.FPTN,
            byedpiWinningArgs = null,
            ipv6Enabled = false,
            trafficMode = TrafficMode.TUN,
            customDnsServers = listOf("1.1.1.1"),
            engineAutoPriority = null,
        )
        val changed = initial.copy(customDnsServers = listOf("8.8.8.8"))

        observer.handle(
            trigger(
                previous = initial.copy(manualEngine = EngineId.BYEDPI),
                snapshot = initial,
            ),
        )
        observer.handle(trigger(previous = initial, snapshot = changed))

        assertEquals(
            listOf(changed),
            restarts,
            "Only the first same-engine startup snapshot can be accepted as already applied. " +
                "A second same-engine snapshot while Probing/Connecting is a real settings change.",
        )
    }
}
