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
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.settings.TrafficMode
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
        assertEquals(TrafficMode.TUN, snap.trafficMode)
        job.cancel()
    }

    @Test
    fun `trafficMode change triggers restart`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())

        val collected = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val job = launch { observer.triggers.toList(collected) }
        advanceUntilIdle()

        flow.emit(SettingsModel.DEFAULT)
        flow.emit(SettingsModel.DEFAULT.copy(trafficMode = TrafficMode.PROXY))
        advanceUntilIdle()

        assertEquals(1, collected.size)
        assertEquals(TrafficMode.PROXY, collected.single().trafficMode)
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

        observer.handle(snapshot)
        assertTrue(restarts.isEmpty(), "Idle: restart skip")

        state.value = TunnelState.Disconnecting
        observer.handle(snapshot)
        assertTrue(restarts.isEmpty(), "Disconnecting: restart skip")

        state.value = TunnelState.Failed(EngineId.BYEDPI, "x")
        observer.handle(snapshot)
        assertTrue(restarts.isEmpty(), "Failed: restart skip")

        state.value = TunnelState.Probing(EngineId.URNETWORK)
        observer.handle(snapshot)
        assertEquals(
            1,
            restarts.size,
            "Probing(URNETWORK) + snapshot=WARP → mismatch → restart обязан фаерить. " +
                "Регрессия 2026-05-20: юзер тапает chip URNETWORK→WARP во время Probing/Connecting, " +
                "debounce 4s проглатывает change пока state ≠ Connected, " +
                "после Connected восстановления триггера нет → chip=WARP но engine=URNETWORK.",
        )

        state.value = TunnelState.Connecting(EngineId.URNETWORK)
        observer.handle(snapshot)
        assertEquals(2, restarts.size, "Connecting(URNETWORK) + snapshot=WARP → mismatch → restart")

        state.value = TunnelState.Connected(EngineId.URNETWORK, 1080)
        observer.handle(snapshot)
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
        observer.handle(snapshot)
        assertTrue(
            restarts.isEmpty(),
            "Probing(WARP) + snapshot=WARP → engine matches → skip. " +
                "Нормальный flow 'тап chip → тап Connect': юзер выбрал WARP, VPN стартует WARP — " +
                "ненужный restart лишних 5-8s replug.",
        )

        state.value = TunnelState.Connecting(EngineId.WARP)
        observer.handle(snapshot)
        assertTrue(restarts.isEmpty(), "Connecting(WARP) + snapshot=WARP → skip")

        state.value = TunnelState.Probing(null)
        observer.handle(snapshot)
        assertTrue(
            restarts.isEmpty(),
            "Probing(null) — pre-permission, VPN service сам подхватит latest manualEngine при выходе. " +
                "Здесь нечего рестартить.",
        )

        state.value = TunnelState.Connected(EngineId.WARP, 1080)
        observer.handle(snapshot)
        assertEquals(
            1,
            restarts.size,
            "Connected(WARP) + snapshot=WARP → restart фаерит (backwards-compat: ipv6/dns/byedpi-args " +
                "ещё могут отличаться, движок прочитает их at-start).",
        )
    }

    @Test
    fun `engineAutoPriority изменился в auto-mode → restart триггерится`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())
        val collected = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val job = launch { observer.triggers.toList(collected) }
        advanceUntilIdle()

        val autoMode = SettingsModel.DEFAULT.copy(manualEngine = null)
        flow.emit(autoMode)
        flow.emit(
            autoMode.copy(
                engineAutoPriority = listOf(EngineId.URNETWORK, EngineId.WARP, EngineId.BYEDPI),
            ),
        )
        advanceUntilIdle()

        assertEquals(
            1,
            collected.size,
            "В auto-mode reorder engine priority должен дропнуть VPN и перевыбрать топ-приоритетный движок. " +
                "Иначе пользователь меняет порядок а соединение продолжает использовать старый выбор — " +
                "новая схема не применяется.",
        )
        assertEquals(
            listOf(EngineId.URNETWORK, EngineId.WARP, EngineId.BYEDPI),
            collected.single().engineAutoPriority,
        )
        job.cancel()
    }

    @Test
    fun `engineAutoPriority изменился в manual-mode → restart НЕ триггерится`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())
        val collected = mutableListOf<EngineSettingsRestartObserver.Snapshot>()
        val job = launch { observer.triggers.toList(collected) }
        advanceUntilIdle()

        val manualMode = SettingsModel.DEFAULT.copy(manualEngine = EngineId.BYEDPI)
        flow.emit(manualMode)
        flow.emit(
            manualMode.copy(
                engineAutoPriority = listOf(EngineId.URNETWORK, EngineId.WARP, EngineId.BYEDPI),
            ),
        )
        advanceUntilIdle()

        assertTrue(
            collected.isEmpty(),
            "В manual-mode auto-priority irrelevant — reorder в Auto-Mode секции настроек не должен " +
                "дропать VPN если юзер вручную выбрал движок.",
        )
        job.cancel()
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
