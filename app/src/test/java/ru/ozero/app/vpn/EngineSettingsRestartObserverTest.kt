package ru.ozero.app.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.EngineId
import ru.ozero.commonvpn.TunnelState
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EngineSettingsRestartObserverTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `triggers drops first emission`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())

        val collected = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val job = launch { observer.triggers.toList(collected) }
        advanceUntilIdle()

        flow.emit(SettingsModel.DEFAULT)
        advanceUntilIdle()

        assertTrue(collected.isEmpty(), "drop(1) must skip the initial emission")
        job.cancel()
    }

    @Test
    fun `triggers emits snapshot when watched fields change`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())

        val collected = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val job = launch { observer.triggers.toList(collected) }
        advanceUntilIdle()

        flow.emit(SettingsModel.DEFAULT)
        flow.emit(
            SettingsModel.DEFAULT.copy(
                manualEngine = EngineId.BYEDPI,
                ipv6Enabled = true,
                byedpiWinningArgs = "  --foo  ",
            ),
        )
        advanceUntilIdle()

        assertEquals(1, collected.size)
        val snap = collected.single()
        assertEquals(EngineId.BYEDPI, snap.manualEngine)
        assertEquals("--foo", snap.byedpiWinningArgs)
        assertEquals(true, snap.ipv6Enabled)
        job.cancel()
    }

    @Test
    fun `splitMode change НЕ триггерит restart — VPN живёт независимо от mode toggle`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())
        val collected = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val job = launch { observer.triggers.toList(collected) }
        advanceUntilIdle()

        flow.emit(SettingsModel.DEFAULT)
        flow.emit(SettingsModel.DEFAULT.copy(splitMode = SplitTunnelMode.ALLOWLIST))
        flow.emit(SettingsModel.DEFAULT.copy(splitMode = SplitTunnelMode.BLOCKLIST))
        flow.emit(SettingsModel.DEFAULT.copy(splitMode = SplitTunnelMode.ALL))
        advanceUntilIdle()

        assertTrue(
            collected.isEmpty(),
            "splitMode toggle не должен валить VPN restart — пользователь крутит вкладки " +
                "Включено/Все/Исключено, каждый restart на Nubia ROM = SIGABRT в libam-go.so. " +
                "splitMode применяется при следующем коннекте (engine читает значение из repo).",
        )
        job.cancel()
    }

    @Test
    fun `triggers ignores changes outside the watched set`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())

        val collected = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val job = launch { observer.triggers.toList(collected) }
        advanceUntilIdle()

        flow.emit(SettingsModel.DEFAULT)
        flow.emit(SettingsModel.DEFAULT.copy(autoStart = true))
        flow.emit(SettingsModel.DEFAULT.copy(autoStart = true, urnetworkEnabled = true))
        advanceUntilIdle()

        assertTrue(collected.isEmpty(), "autoStart/urnetwork are not in the watched set")
        job.cancel()
    }

    @Test
    fun `triggers debounce коалесцирует chain быстрых изменений`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 16)
        val observer = newObserver(flow, alwaysConnected())
        val collected = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val job = launch { observer.triggers.toList(collected) }
        advanceUntilIdle()

        flow.emit(SettingsModel.DEFAULT)
        advanceUntilIdle()
        flow.emit(SettingsModel.DEFAULT.copy(manualEngine = EngineId.BYEDPI))
        flow.emit(SettingsModel.DEFAULT.copy(manualEngine = EngineId.WARP))
        flow.emit(SettingsModel.DEFAULT.copy(manualEngine = EngineId.URNETWORK))
        flow.emit(SettingsModel.DEFAULT.copy(manualEngine = EngineId.BYEDPI))
        flow.emit(SettingsModel.DEFAULT.copy(manualEngine = EngineId.WARP))
        advanceUntilIdle()

        assertEquals(
            1,
            collected.size,
            "5 быстрых engine-toggle обязаны коалесцироваться в 1 emit после debounce — иначе " +
                "MainActivity триггерит chain restart VPN, что роняет URnetwork (Go runtime " +
                "conflict), сбивает IP fetch warmup и убивает стабильность.",
        )
        assertEquals(EngineId.WARP, collected.single().manualEngine)
        job.cancel()
    }

    @Test
    fun `handle invokes restart only when state is Connected`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val state = MutableStateFlow<TunnelState>(TunnelState.Idle)
        val restarts = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val observer = EngineSettingsRestartObserver(
            settingsFlow = flow,
            vpnStateProvider = { state.value },
            onRestartConnected = { restarts += it },
        )

        val snapshot = EngineSettingsRestartObserver.Snapshot(
            manualEngine = EngineId.BYEDPI,
            byedpiWinningArgs = null,
            ipv6Enabled = false,
            customDnsServers = emptyList(),
        )
        observer.handle(snapshot)
        assertTrue(restarts.isEmpty(), "no restart while Idle")

        state.value = TunnelState.Connected(EngineId.BYEDPI, 1080)
        observer.handle(snapshot)
        assertEquals(listOf(snapshot), restarts, "restart fires when Connected")
    }

    @Test
    fun `RESTART_DEBOUNCE_MS не меньше 3000 — защита от engine-restart storm при split-tunnel toggle`() {
        val source = java.io.File(
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

    private fun newObserver(
        flow: MutableSharedFlow<SettingsModel>,
        stateProvider: () -> TunnelState,
    ): EngineSettingsRestartObserver = EngineSettingsRestartObserver(
        settingsFlow = flow,
        vpnStateProvider = stateProvider,
        onRestartConnected = {},
    )

    private fun alwaysConnected(): () -> TunnelState = {
        TunnelState.Connected(engineId = EngineId.BYEDPI, socksPort = 1080)
    }
}
