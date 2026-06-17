package ru.ozero.app.vpn

import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.TrafficMode

@OptIn(ExperimentalCoroutinesApi::class)
class EngineSettingsRestartObserverHandleConnectedTest : EngineSettingsRestartObserverTestBase() {

    @Test
    fun `handle restart — connected engine differs from new target`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Connected(EngineId.FPTN, 0))
        val restarts = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val observer = EngineSettingsRestartObserver(
            settingsFlow = flow,
            vpnStateProvider = { state.value },
            onRestartConnected = { restarts += it },
        )
        val snapshot = EngineSettingsRestartObserver.Snapshot(
            manualEngine = EngineId.WARP,
            byedpiWinningArgs = null,
            byedpiUseUiMode = false,
            ipv6Enabled = false,
            trafficMode = TrafficMode.TUN,
            customDnsServers = emptyList(),
            engineAutoPriority = null,
        )

        observer.handle(trigger(snapshot, snapshot))

        assertEquals(1, restarts.size, "Connected(FPTN) + target=WARP → user-visible engine switch restart")
    }

    @Test
    fun `handle restart — connected same engine with new settings after startup`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Connected(EngineId.FPTN, 0))
        val restarts = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val observer = EngineSettingsRestartObserver(
            settingsFlow = flow,
            vpnStateProvider = { state.value },
            onRestartConnected = { restarts += it },
        )
        val snapshot = EngineSettingsRestartObserver.Snapshot(
            manualEngine = EngineId.FPTN,
            byedpiWinningArgs = null,
            byedpiUseUiMode = false,
            ipv6Enabled = false,
            trafficMode = TrafficMode.PROXY,
            customDnsServers = emptyList(),
            engineAutoPriority = null,
        )

        observer.handle(trigger(previous = snapshot.copy(trafficMode = TrafficMode.TUN), snapshot = snapshot))

        assertEquals(
            1,
            restarts.size,
            "A same-engine settings change that appears after stable Connected is user-visible and must restart.",
        )
    }

    @Test
    fun `handle restart — connected ByeDPI restarts when only UI mode changes`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Connected(EngineId.BYEDPI, 1080))
        val restarts = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val observer = EngineSettingsRestartObserver(
            settingsFlow = flow,
            vpnStateProvider = { state.value },
            onRestartConnected = { restarts += it },
        )
        val previous = snapshot(
            manualEngine = EngineId.BYEDPI,
            byedpiWinningArgs = "-Y -Ar -s5",
            byedpiUseUiMode = true,
        )
        val applied = previous.copy(byedpiUseUiMode = false)

        observer.handle(trigger(previous = previous, snapshot = applied))

        assertEquals(
            listOf(applied),
            restarts,
            "Apply can change only ByeDPI mode when args already match; running UI config must restart.",
        )
    }

    @Test
    fun `RESTART_DEBOUNCE_MS не меньше 3000 — защита от engine-restart storm при split-tunnel toggle`() {
        val source = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/vpn/EngineSettingsRestartObserver.kt",
        ).readText()
        val regex = Regex("RESTART_DEBOUNCE_MS\\s*=\\s*(\\d[\\d_]*)L")
        val m = regex.find(source) ?: error("RESTART_DEBOUNCE_MS не найден")
        val ms = m.groupValues[1].replace("_", "").toLong()
        assertTrue(
            ms >= 3_000L,
            "RESTART_DEBOUNCE_MS обязан быть >= 3000ms — каждый restart engine во время " +
                "split-tunnel toggling рискует SIGABRT в libgojni gcWriteBarrier (dual Go runtime conflict, " +
                "warp-handle-leak-sigabrt). Меньшее окно → restart-storm → нативный краш. Fact=$ms",
        )
    }
}
