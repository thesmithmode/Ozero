package ru.ozero.app.vpn

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.settings.SettingsModel
import ru.ozero.enginescore.settings.SplitTunnelMode
import ru.ozero.enginescore.settings.TrafficMode
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EngineSettingsRestartObserverTriggersTest : EngineSettingsRestartObserverTestBase() {

    @Test
    fun `triggers drops first emission`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())

        val collected = mutableListOf<EngineSettingsRestartObserver.Trigger>()
        val job = collectTriggers(observer, collected)
        advanceUntilIdle()

        flow.emit(SettingsModel.DEFAULT)
        advanceRestartDebounce()
        advanceUntilIdle()

        assertTrue(collected.isEmpty(), "drop(1) must skip the initial emission")
        job.cancel()
    }

    @Test
    fun `triggers emits snapshot when watched fields change`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())

        val collected = mutableListOf<EngineSettingsRestartObserver.Trigger>()
        val job = collectTriggers(observer, collected)
        advanceUntilIdle()

        flow.emit(SettingsModel.DEFAULT)
        advanceRestartDebounce()
        advanceUntilIdle()
        flow.emit(
            SettingsModel.DEFAULT.copy(
                manualEngine = EngineId.BYEDPI,
                ipv6Enabled = true,
                byedpiWinningArgs = "  --foo  ",
            ),
        )
        advanceRestartDebounce()
        advanceUntilIdle()

        assertEquals(1, collected.size)
        val snap = collected.single().snapshot
        assertEquals(EngineId.BYEDPI, snap.manualEngine)
        assertEquals("--foo", snap.byedpiWinningArgs)
        assertEquals(true, snap.ipv6Enabled)
        assertEquals(TrafficMode.TUN, snap.trafficMode)
        job.cancel()
    }

    @Test
    fun `triggers keeps initial baseline when first change happens inside debounce window`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())
        val collected = mutableListOf<EngineSettingsRestartObserver.Trigger>()
        val job = collectTriggers(observer, collected)
        advanceUntilIdle()

        flow.emit(SettingsModel.DEFAULT)
        flow.emit(SettingsModel.DEFAULT.copy(customDnsServers = listOf("8.8.8.8")))
        advanceRestartDebounce()
        advanceUntilIdle()

        assertEquals(1, collected.size)
        assertEquals(SettingsModel.DEFAULT.customDnsServers, collected.single().previous.customDnsServers)
        assertEquals(listOf("8.8.8.8"), collected.single().snapshot.customDnsServers)
        job.cancel()
    }

    @Test
    fun `trafficMode change triggers restart`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())

        val collected = mutableListOf<EngineSettingsRestartObserver.Trigger>()
        val job = collectTriggers(observer, collected)
        advanceUntilIdle()

        flow.emit(SettingsModel.DEFAULT)
        advanceRestartDebounce()
        advanceUntilIdle()
        flow.emit(SettingsModel.DEFAULT.copy(trafficMode = TrafficMode.PROXY))
        advanceRestartDebounce()
        advanceUntilIdle()

        assertEquals(1, collected.size)
        assertEquals(TrafficMode.PROXY, collected.single().snapshot.trafficMode)
        job.cancel()
    }

    @Test
    fun `splitMode change НЕ триггерит restart — VPN живёт независимо от mode toggle`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())
        val collected = mutableListOf<EngineSettingsRestartObserver.Trigger>()
        val job = collectTriggers(observer, collected)
        advanceUntilIdle()

        flow.emit(SettingsModel.DEFAULT)
        advanceRestartDebounce()
        advanceUntilIdle()
        flow.emit(SettingsModel.DEFAULT.copy(splitMode = SplitTunnelMode.ALLOWLIST))
        flow.emit(SettingsModel.DEFAULT.copy(splitMode = SplitTunnelMode.BLOCKLIST))
        flow.emit(SettingsModel.DEFAULT.copy(splitMode = SplitTunnelMode.ALL))
        advanceRestartDebounce()
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

        val collected = mutableListOf<EngineSettingsRestartObserver.Trigger>()
        val job = collectTriggers(observer, collected)
        advanceUntilIdle()

        flow.emit(SettingsModel.DEFAULT)
        advanceRestartDebounce()
        advanceUntilIdle()
        flow.emit(SettingsModel.DEFAULT.copy(autoStart = true))
        flow.emit(SettingsModel.DEFAULT.copy(autoStart = true, urnetworkEnabled = true))
        advanceRestartDebounce()
        advanceUntilIdle()

        assertTrue(collected.isEmpty(), "autoStart/urnetwork are not in the watched set")
        job.cancel()
    }

    @Test
    fun `triggers debounce коалесцирует chain быстрых изменений`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 16)
        val observer = newObserver(flow, alwaysConnected())
        val collected = mutableListOf<EngineSettingsRestartObserver.Trigger>()
        val job = collectTriggers(observer, collected)
        advanceUntilIdle()

        flow.emit(SettingsModel.DEFAULT)
        advanceRestartDebounce()
        advanceUntilIdle()
        flow.emit(SettingsModel.DEFAULT.copy(manualEngine = EngineId.BYEDPI))
        flow.emit(SettingsModel.DEFAULT.copy(manualEngine = EngineId.WARP))
        flow.emit(SettingsModel.DEFAULT.copy(manualEngine = EngineId.URNETWORK))
        flow.emit(SettingsModel.DEFAULT.copy(manualEngine = EngineId.BYEDPI))
        flow.emit(SettingsModel.DEFAULT.copy(manualEngine = EngineId.WARP))
        advanceRestartDebounce()
        advanceUntilIdle()

        assertEquals(
            1,
            collected.size,
            "5 быстрых engine-toggle обязаны коалесцироваться в 1 emit после debounce — иначе " +
                "MainActivity триггерит chain restart VPN, что роняет URnetwork (Go runtime " +
                "conflict), сбивает IP fetch warmup и убивает стабильность.",
        )
        assertEquals(EngineId.WARP, collected.single().snapshot.manualEngine)
        job.cancel()
    }

    @Test
    fun `engineAutoPriority изменился в auto-mode → restart триггерится`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())
        val collected = mutableListOf<EngineSettingsRestartObserver.Trigger>()
        val job = collectTriggers(observer, collected)
        advanceUntilIdle()

        val autoMode = SettingsModel.DEFAULT.copy(manualEngine = null)
        flow.emit(autoMode)
        advanceRestartDebounce()
        advanceUntilIdle()
        flow.emit(
            autoMode.copy(
                engineAutoPriority = listOf(EngineId.URNETWORK, EngineId.WARP, EngineId.BYEDPI),
            ),
        )
        advanceRestartDebounce()
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
            collected.single().snapshot.engineAutoPriority,
        )
        job.cancel()
    }

    @Test
    fun `engineAutoPriority изменился в manual-mode → restart НЕ триггерится`() = runTest(dispatcher) {
        val flow = MutableSharedFlow<SettingsModel>(replay = 0, extraBufferCapacity = 8)
        val observer = newObserver(flow, alwaysConnected())
        val collected = mutableListOf<EngineSettingsRestartObserver.Trigger>()
        val job = collectTriggers(observer, collected)
        advanceUntilIdle()

        val manualMode = SettingsModel.DEFAULT.copy(manualEngine = EngineId.BYEDPI)
        flow.emit(manualMode)
        advanceRestartDebounce()
        advanceUntilIdle()
        flow.emit(
            manualMode.copy(
                engineAutoPriority = listOf(EngineId.URNETWORK, EngineId.WARP, EngineId.BYEDPI),
            ),
        )
        advanceRestartDebounce()
        advanceUntilIdle()

        assertTrue(
            collected.isEmpty(),
            "В manual-mode auto-priority irrelevant — reorder в Auto-Mode секции настроек не должен " +
                "дропать VPN если юзер вручную выбрал движок.",
        )
        job.cancel()
    }
}
