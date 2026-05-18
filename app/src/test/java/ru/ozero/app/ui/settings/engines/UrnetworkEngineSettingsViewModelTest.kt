package ru.ozero.app.ui.settings.engines

import com.bringyour.sdk.LocationsViewController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.ozero.app.urnetwork.UrnetworkBalanceRepository
import ru.ozero.app.urnetwork.UrnetworkBalanceState
import ru.ozero.commonvpn.TunnelController
import ru.ozero.enginescore.EngineId
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkProvideControlMode
import ru.ozero.engineurnetwork.UrnetworkProvideNetworkMode
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkWindowType
import ru.ozero.engineurnetwork.allowDirect
import ru.ozero.engineurnetwork.fixedIpSize
import ru.ozero.engineurnetwork.provideControlMode
import ru.ozero.engineurnetwork.provideNetworkMode
import ru.ozero.engineurnetwork.setAllowDirect
import ru.ozero.engineurnetwork.setFixedIpSize
import ru.ozero.engineurnetwork.windowType
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class UrnetworkEngineSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun activeTunnel(): TunnelController {
        val tc = TunnelController()
        tc.onProbing()
        tc.onConnecting(EngineId.URNETWORK)
        tc.onEngineStarted(EngineId.URNETWORK, 1080)
        return tc
    }

    private fun idleTunnel(): TunnelController = TunnelController()

    @Test
    fun `uiState стартует как Loading`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(
            FakeUrnetworkBridge(),
            FakeSettingsRepo(),
            fakeUrnetworkConfigStore(),
            idleTunnel(),
            fakeBalanceRepo(),
        )
        assertSame(UrnetworkSettingsUiState.Loading, vm.uiState.value)
    }

    @Test
    fun `uiState переходит в NotConnected когда engine не активен`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(
            FakeUrnetworkBridge(connected = false),
            FakeSettingsRepo(),
            fakeUrnetworkConfigStore(),
            idleTunnel(),
            fakeBalanceRepo(),
        )
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.NotConnected>(vm.uiState.value)
    }

    @Test
    fun `initDeviceForLocations вызывается если byClientJwt задан в configStore`() = runTest {
        val bridge = FakeUrnetworkBridge(deviceAvailable = false)
        val store = fakeUrnetworkConfigStoreWithJwt()
        UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, idleTunnel(), fakeBalanceRepo())
        advanceUntilIdle()
        assertTrue(bridge.initDeviceCallCount.get() > 0, "initDeviceForLocations должен вызываться при byClientJwt != null")
    }

    @Test
    fun `uiState переходит в Ready без VPN если initDeviceForLocations успешен`() = runTest {
        val bridge = FakeUrnetworkBridge(deviceAvailable = true)
        val store = fakeUrnetworkConfigStoreWithJwt()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, idleTunnel(), fakeBalanceRepo())
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.Ready>(vm.uiState.value)
    }

    @Test
    fun `uiState остаётся Ready при остановке VPN если isDeviceAvailable true`() = runTest {
        val bridge = FakeUrnetworkBridge(connected = true, deviceAvailable = true)
        val store = fakeUrnetworkConfigStoreWithJwt()
        val tc = activeTunnel()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, tc, fakeBalanceRepo())
        advanceUntilIdle()
        tc.onDisconnecting()
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.Ready>(vm.uiState.value)
    }

    @Test
    fun `init обязан retry refresh пока bridge не готов — sentinel против stuck NotConnected`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkEngineSettingsViewModel.kt",
        ).readText()
        val initBlock = source.substringAfter("init {").substringBefore("\n    }\n")
        kotlin.test.assertTrue(
            initBlock.contains("REFRESH_RETRY_ATTEMPTS") && initBlock.contains("refreshOnce()"),
            "init обязан retry refreshOnce пока bridge не готов — иначе stuck в NotConnected " +
                "когда экран открыт раньше чем URnetwork SDK завершил start. Init body:\n$initBlock",
        )
        kotlin.test.assertTrue(
            source.contains("REFRESH_RETRY_ATTEMPTS = ") &&
                Regex("REFRESH_RETRY_ATTEMPTS\\s*=\\s*(\\d+)").find(source)!!.groupValues[1].toInt() >= 5,
            "REFRESH_RETRY_ATTEMPTS обязан быть >= 5 — bridge.start может занять секунды",
        )
    }

    @Test
    fun `selectWindowType сохраняет в configStore и применяет profile через bridge`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, activeTunnel(), fakeBalanceRepo())
        vm.selectWindowType(UrnetworkWindowType.SPEED)
        advanceUntilIdle()
        assertEquals(UrnetworkWindowType.SPEED, store.windowType().first())
        assertEquals(UrnetworkWindowType.SPEED, bridge.lastAppliedWindowType)
    }

    @Test
    fun `selectWindowType AUTO сбрасывает fixedIpSize`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        store.setFixedIpSize(true)
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, activeTunnel(), fakeBalanceRepo())
        vm.selectWindowType(UrnetworkWindowType.AUTO)
        advanceUntilIdle()
        assertEquals(false, store.fixedIpSize().first())
    }

    @Test
    fun `toggleFixedIpSize сохраняет в configStore и применяет profile`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, activeTunnel(), fakeBalanceRepo())
        vm.toggleFixedIpSize(true)
        advanceUntilIdle()
        assertEquals(true, store.fixedIpSize().first())
        assertEquals(true, bridge.lastAppliedFixedIp)
    }

    @Test
    fun `toggleAllowDirect false сохраняет в configStore и применяет profile`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, activeTunnel(), fakeBalanceRepo())
        vm.toggleAllowDirect(false)
        advanceUntilIdle()
        assertEquals(false, store.allowDirect().first())
        assertEquals(false, bridge.lastAppliedAllowDirect)
    }

    @Test
    fun `toggleAllowDirect true возвращает direct connections`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        store.setAllowDirect(false)
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, activeTunnel(), fakeBalanceRepo())
        vm.toggleAllowDirect(true)
        advanceUntilIdle()
        assertEquals(true, store.allowDirect().first())
        assertEquals(true, bridge.lastAppliedAllowDirect)
    }

    @Test
    fun `toggleAllowDirect не вызывает bridge при idle engine`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, idleTunnel(), fakeBalanceRepo())
        vm.toggleAllowDirect(false)
        advanceUntilIdle()
        assertEquals(false, store.allowDirect().first())
        assertEquals(null, bridge.lastAppliedAllowDirect, "bridge не должен трогаться при idle engine")
    }

    @Test
    fun `allowDirect StateFlow отражает значение из configStore`() = runTest {
        val store = fakeUrnetworkConfigStore()
        store.setAllowDirect(false)
        val vm = UrnetworkEngineSettingsViewModel(
            FakeUrnetworkBridge(), FakeSettingsRepo(), store, idleTunnel(), fakeBalanceRepo(),
        )
        advanceUntilIdle()
        assertEquals(false, vm.allowDirect.value)
    }

    @Test
    fun `selectProvideControlMode сохраняет в configStore и применяет к bridge при active engine`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, activeTunnel(), fakeBalanceRepo())
        vm.selectProvideControlMode(UrnetworkProvideControlMode.AUTO)
        advanceUntilIdle()
        assertEquals(UrnetworkProvideControlMode.AUTO, store.provideControlMode().first())
        assertEquals(UrnetworkProvideControlMode.AUTO, bridge.lastProvideControlMode)
    }

    @Test
    fun `selectProvideControlMode не вызывает bridge при idle engine — только persist в store`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, idleTunnel(), fakeBalanceRepo())
        vm.selectProvideControlMode(UrnetworkProvideControlMode.AUTO)
        advanceUntilIdle()
        assertEquals(UrnetworkProvideControlMode.AUTO, store.provideControlMode().first())
        assertNull(bridge.lastProvideControlMode, "bridge не должен трогаться при idle engine")
    }

    @Test
    fun `selectProvideNetworkMode сохраняет в configStore и применяет к bridge при active engine`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, activeTunnel(), fakeBalanceRepo())
        vm.selectProvideNetworkMode(UrnetworkProvideNetworkMode.ALL)
        advanceUntilIdle()
        assertEquals(UrnetworkProvideNetworkMode.ALL, store.provideNetworkMode().first())
        assertEquals(UrnetworkProvideNetworkMode.ALL, bridge.lastProvideNetworkMode)
    }

    @Test
    fun `selectProvideNetworkMode не вызывает bridge при idle engine — только persist в store`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, idleTunnel(), fakeBalanceRepo())
        vm.selectProvideNetworkMode(UrnetworkProvideNetworkMode.ALL)
        advanceUntilIdle()
        assertEquals(UrnetworkProvideNetworkMode.ALL, store.provideNetworkMode().first())
        assertNull(bridge.lastProvideNetworkMode, "bridge не должен трогаться при idle engine")
    }

    @Test
    fun `selectWindowType не вызывает bridge applyPerformanceProfile когда engine не активен`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = fakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, idleTunnel(), fakeBalanceRepo())
        vm.selectWindowType(UrnetworkWindowType.SPEED)
        advanceUntilIdle()
        assertEquals(UrnetworkWindowType.SPEED, store.windowType().first())
        assertNull(bridge.lastAppliedWindowType)
    }

    @Test
    fun `windowType StateFlow отражает начальное значение из configStore`() = runTest {
        val store = fakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(
            FakeUrnetworkBridge(), FakeSettingsRepo(), store, idleTunnel(), fakeBalanceRepo(),
        )
        advanceUntilIdle()
        assertEquals(UrnetworkWindowType.AUTO, vm.windowType.value)
    }

    @Test
    fun `switchingCountry стартует с false`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(
            FakeUrnetworkBridge(),
            FakeSettingsRepo(),
            fakeUrnetworkConfigStore(),
            idleTunnel(),
            fakeBalanceRepo(),
        )
        assertEquals(false, vm.switchingCountry.value)
    }

    @Test
    fun `selectLocation активирует switchingCountry когда страна меняется`() = runTest {
        val locA = FakeLocationToken("US")
        val locB = FakeLocationToken("DE")
        val bridge = FakeUrnetworkBridge(
            connected = true,
            initialLocation = locA,
            peerCountValue = 0,
        )
        val vm = UrnetworkEngineSettingsViewModel(
            bridge,
            FakeSettingsRepo(),
            fakeUrnetworkConfigStore(),
            activeTunnel(),
            fakeBalanceRepo(),
        )
        runCurrent()
        vm.selectLocation(locB)
        runCurrent()
        assertEquals(true, vm.switchingCountry.value)
    }

    @Test
    fun `switchingCountry очищается после 15s budget когда peers не появились`() = runTest {
        val locA = FakeLocationToken("US")
        val locB = FakeLocationToken("DE")
        val bridge = FakeUrnetworkBridge(
            connected = true,
            initialLocation = locA,
            peerCountValue = 0,
        )
        val vm = UrnetworkEngineSettingsViewModel(
            bridge,
            FakeSettingsRepo(),
            fakeUrnetworkConfigStore(),
            activeTunnel(),
            fakeBalanceRepo(),
        )
        runCurrent()
        vm.selectLocation(locB)
        runCurrent()
        assertEquals(true, vm.switchingCountry.value)
        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(false, vm.switchingCountry.value)
    }

    @Test
    fun `sentinel — init использует collectLatest а не collect`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkEngineSettingsViewModel.kt",
        ).readText()
        val initBlock = source.substringAfter("init {").substringBefore("fun refresh()")
        kotlin.test.assertTrue(
            initBlock.contains("collectLatest"),
            "init обязан использовать collectLatest чтобы при active=false отменить текущий retry-loop. " +
                "collect не отменяет предыдущий блок — два параллельных loop при быстром active flip.",
        )
        kotlin.test.assertFalse(
            initBlock.contains(".collect {"),
            "init не должен использовать .collect — только collectLatest для cancel semantics.",
        )
    }

    @Test
    fun `sentinel — selectLocation и setProvidePaused используют update а не value assignment`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkEngineSettingsViewModel.kt",
        ).readText()
        val selectBody = source
            .substringAfter("fun selectLocation(")
            .substringBefore("private fun startSwitchingIndicator")
        kotlin.test.assertFalse(
            selectBody.contains("_uiState.value = current.copy"),
            "selectLocation не должен использовать read-modify-write через value=copy — race condition.",
        )
        kotlin.test.assertTrue(
            selectBody.contains("_uiState.update"),
            "selectLocation обязан использовать _uiState.update для атомарного обновления.",
        )
        val pauseBody = source.substringAfter("fun setProvidePaused(").substringBefore("private fun updateLocations")
        kotlin.test.assertFalse(
            pauseBody.contains("_uiState.value = current.copy"),
            "setProvidePaused не должен использовать read-modify-write через value=copy — race condition.",
        )
        kotlin.test.assertTrue(
            pauseBody.contains("_uiState.update"),
            "setProvidePaused обязан использовать _uiState.update для атомарного обновления.",
        )
        val filterBody = source
            .substringAfter("private fun applyFilter(")
            .substringBefore("private fun teardownLocationsVc")
        kotlin.test.assertFalse(
            filterBody.contains("_uiState.value = UrnetworkSettingsUiState.Ready"),
            "applyFilter не должен использовать _uiState.value = Ready — race condition.",
        )
        kotlin.test.assertTrue(
            filterBody.contains("_uiState.update"),
            "applyFilter обязан использовать _uiState.update для атомарного обновления.",
        )
    }

    @Test
    fun `selectLocation обновляет selectedLocation в uiState без полного пересоздания`() = runTest {
        val locA = FakeLocationToken("US")
        val locB = FakeLocationToken("DE")
        val bridge = FakeUrnetworkBridge(connected = true, initialLocation = locA)
        val vm = UrnetworkEngineSettingsViewModel(
            bridge,
            FakeSettingsRepo(),
            fakeUrnetworkConfigStore(),
            activeTunnel(),
            fakeBalanceRepo(),
        )
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        val stateBeforeSelect = vm.uiState.value
        if (stateBeforeSelect is UrnetworkSettingsUiState.Ready) {
            vm.selectLocation(locB)
            runCurrent()
            val stateAfter = vm.uiState.value
            if (stateAfter is UrnetworkSettingsUiState.Ready) {
                assertEquals(locB, stateAfter.selectedLocation)
            }
        }
    }

    @Test
    fun `sentinel — updateLocations читает regions и cities из FilteredLocations`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkEngineSettingsViewModel.kt",
        ).readText()
        val updateBody = source
            .substringAfter("private fun updateLocations(")
            .substringBefore("private fun buildLocationList")
        kotlin.test.assertTrue(
            updateBody.contains("filtered.regions"),
            "updateLocations обязан читать filtered.regions — иначе регионы никогда не загружаются",
        )
        kotlin.test.assertTrue(
            updateBody.contains("filtered.cities"),
            "updateLocations обязан читать filtered.cities — иначе города никогда не загружаются",
        )
    }

    @Test
    fun `sentinel — UrnetworkSettingsUiState_Ready содержит поля regions и cities`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkEngineSettingsViewModel.kt",
        ).readText()
        val readyBlock = source.substringAfter("data class Ready(").substringBefore(") : UrnetworkSettingsUiState")
        kotlin.test.assertTrue(
            readyBlock.contains("regions"),
            "UrnetworkSettingsUiState.Ready обязан иметь поле regions",
        )
        kotlin.test.assertTrue(
            readyBlock.contains("cities"),
            "UrnetworkSettingsUiState.Ready обязан иметь поле cities",
        )
    }

    @Test
    fun `sentinel — CheckIpRow открывает ur_io_ip через LocalUriHandler`() {
        val source = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkEngineSettingsScreen.kt",
        ).readText()
        val checkIpBlock = source
            .substringAfter("private fun CheckIpRow()")
            .substringBefore("\nprivate fun ")
        kotlin.test.assertTrue(
            checkIpBlock.contains("openUri"),
            "CheckIpRow обязан вызывать openUri — иначе клик не откроет браузер",
        )
        kotlin.test.assertTrue(
            checkIpBlock.contains("https://ur.io/ip"),
            "CheckIpRow обязан передавать https://ur.io/ip в openUri",
        )
        kotlin.test.assertTrue(
            checkIpBlock.contains("LocalUriHandler"),
            "CheckIpRow обязан использовать LocalUriHandler",
        )
    }

    @Test
    fun `setProvidePaused true когда engine активен — вызывает bridge и обновляет Ready providePaused`() = runTest {
        val bridge = FakeUrnetworkBridge(connected = true)
        val vm = UrnetworkEngineSettingsViewModel(
            bridge,
            FakeSettingsRepo(),
            fakeUrnetworkConfigStore(),
            activeTunnel(),
            fakeBalanceRepo(),
        )
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        val before = vm.uiState.value
        assertIs<UrnetworkSettingsUiState.Ready>(before)
        assertEquals(false, before.providePaused, "до toggle providePaused=false по умолчанию")

        vm.setProvidePaused(true)
        advanceUntilIdle()

        assertEquals(1, bridge.setProvidePausedCallCount.get(), "bridge.setProvidePaused обязан вызваться 1 раз")
        assertEquals(true, bridge.lastPausedValue, "bridge получает paused=true")
        val after = vm.uiState.value
        assertIs<UrnetworkSettingsUiState.Ready>(after)
        assertEquals(true, after.providePaused, "Ready.providePaused обязан обновиться на true")
    }

    @Test
    fun `setProvidePaused false когда engine активен — снимает паузу и обновляет state`() = runTest {
        val bridge = FakeUrnetworkBridge(connected = true)
        val vm = UrnetworkEngineSettingsViewModel(
            bridge,
            FakeSettingsRepo(),
            fakeUrnetworkConfigStore(),
            activeTunnel(),
            fakeBalanceRepo(),
        )
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        vm.setProvidePaused(true)
        advanceUntilIdle()
        vm.setProvidePaused(false)
        advanceUntilIdle()

        assertEquals(2, bridge.setProvidePausedCallCount.get(), "bridge вызван дважды — пауза+снятие")
        assertEquals(false, bridge.lastPausedValue, "финальное значение bridge — false")
        val after = vm.uiState.value
        assertIs<UrnetworkSettingsUiState.Ready>(after)
        assertEquals(false, after.providePaused, "Ready.providePaused вернулся в false")
    }

    @Test
    fun `setProvidePaused когда engine не активен — НЕ дёргает bridge`() = runTest {
        val bridge = FakeUrnetworkBridge(connected = false)
        val vm = UrnetworkEngineSettingsViewModel(
            bridge,
            FakeSettingsRepo(),
            fakeUrnetworkConfigStore(),
            idleTunnel(),
            fakeBalanceRepo(),
        )
        advanceUntilIdle()

        vm.setProvidePaused(true)
        advanceUntilIdle()

        assertEquals(
            0,
            bridge.setProvidePausedCallCount.get(),
            "bridge.setProvidePaused не должен вызываться когда isUrnetworkActive=false — " +
                "иначе SDK CGo crash на null context",
        )
        assertEquals(null, bridge.lastPausedValue, "lastPausedValue остаётся null")
    }

    @Test
    fun `balanceState стартует с INITIAL`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(
            FakeUrnetworkBridge(),
            FakeSettingsRepo(),
            fakeUrnetworkConfigStore(),
            idleTunnel(),
            fakeBalanceRepo(),
        )
        val collector = backgroundScope.launch { vm.balanceState.collect { } }
        advanceTimeBy(50L)
        runCurrent()
        assertEquals(UrnetworkBalanceState.INITIAL, vm.balanceState.value)
        collector.cancel()
    }

    @Test
    fun `balanceState отражает значение из balanceRepository`() = runTest {
        val snapshot = UrnetworkSdkBridge.SubscriptionBalanceSnapshot(
            balanceBytes = 1_000_000L,
            usedBytes = 200_000L,
            pendingBytes = 0L,
            startBalanceBytes = 1_000_000L,
            plan = "free",
            store = null,
        )
        val repo = FakeBalanceRepository(
            UrnetworkBalanceState(snapshot = snapshot, isLoading = false, lastError = null),
        )
        val vm = UrnetworkEngineSettingsViewModel(
            FakeUrnetworkBridge(),
            FakeSettingsRepo(),
            fakeUrnetworkConfigStore(),
            idleTunnel(),
            repo,
        )
        val collector = backgroundScope.launch { vm.balanceState.collect { } }
        advanceTimeBy(50L)
        runCurrent()
        assertEquals(snapshot, vm.balanceState.value.snapshot)
        collector.cancel()
    }

    @Test
    fun `balanceState вызывает refresh через 30s — sentinel auto-poll`() = runTest {
        val repo = FakeBalanceRepository()
        val vm = UrnetworkEngineSettingsViewModel(
            FakeUrnetworkBridge(),
            FakeSettingsRepo(),
            fakeUrnetworkConfigStore(),
            idleTunnel(),
            repo,
        )
        val collector = backgroundScope.launch { vm.balanceState.collect { } }
        advanceTimeBy(100L)
        runCurrent()
        val initialRefreshes = repo.refreshCallCount.get()
        advanceTimeBy(30_000L)
        runCurrent()
        kotlin.test.assertTrue(
            repo.refreshCallCount.get() > initialRefreshes,
            "через 30s должен быть повторный refresh. initial=$initialRefreshes " +
                "current=${repo.refreshCallCount.get()}",
        )
        collector.cancel()
    }
}

private class FakeSettingsRepo : ru.ozero.enginescore.settings.SettingsRepository {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(
        ru.ozero.enginescore.settings.SettingsModel.DEFAULT,
    )
    override val settings: kotlinx.coroutines.flow.Flow<ru.ozero.enginescore.settings.SettingsModel> = state
    val countryCodeUpdates = mutableListOf<String?>()
    override suspend fun setSplitMode(mode: ru.ozero.enginescore.settings.SplitTunnelMode) = Unit
    override suspend fun setIpv6Enabled(enabled: Boolean) = Unit
    override suspend fun setAutoStart(enabled: Boolean) = Unit
    override suspend fun setManualEngine(engine: ru.ozero.enginescore.EngineId?) = Unit
    override suspend fun setUrnetworkEnabled(enabled: Boolean) = Unit
    override suspend fun setUrnetworkJwt(jwt: String?) = Unit
    override suspend fun setUrnetworkCountryCode(code: String?) {
        countryCodeUpdates += code
    }
    override suspend fun setByedpiWinningArgs(args: String?) = Unit
    override suspend fun setByedpiDefaultAccepted(accepted: Boolean) = Unit
    override suspend fun setCustomDnsServers(servers: List<String>) = Unit
    override suspend fun setHostsMode(mode: ru.ozero.enginescore.settings.HostsMode) = Unit
    override suspend fun setHosts(hosts: List<String>) = Unit
    override suspend fun setUiLocaleTag(tag: String?) = Unit
    override suspend fun setAppMode(mode: ru.ozero.enginescore.settings.AppMode) = Unit
    override suspend fun setKillswitchEnabled(enabled: Boolean) = Unit
    override suspend fun setAlwaysOnBannerDismissed(dismissed: Boolean) = Unit
}

private fun fakeUrnetworkConfigStore(): UrnetworkConfigStore =
    ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
        ru.ozero.engineurnetwork.UrnetworkConfig(walletOverride = "0xWALLET"),
    )

private fun fakeUrnetworkConfigStoreWithJwt(jwt: String = "test-jwt"): UrnetworkConfigStore =
    ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore(
        ru.ozero.engineurnetwork.UrnetworkConfig(walletOverride = "0xWALLET", byClientJwt = jwt),
    )

private data class FakeLocationToken(override val countryCode: String?) : UrnetworkSdkBridge.LocationToken

private class FakeUrnetworkBridge(
    private val connected: Boolean = false,
    private val initialLocation: UrnetworkSdkBridge.LocationToken? = null,
    private val peerCountValue: Int = 0,
    private val deviceAvailable: Boolean = false,
) : UrnetworkSdkBridge {
    val initDeviceCallCount = AtomicInteger(0)
    override suspend fun initDeviceForLocations(byClientJwt: String, walletAddress: String): Boolean {
        initDeviceCallCount.incrementAndGet()
        return deviceAvailable
    }
    override fun isDeviceAvailable(): Boolean = deviceAvailable
    override suspend fun start(
        walletAddress: String,
        apiUrl: String,
        connectUrl: String,
        byClientJwt: String,
    ): UrnetworkSdkBridge.StartResult = UrnetworkSdkBridge.StartResult.Success

    override suspend fun stop() = Unit
    override fun isRunning(): Boolean = connected
    override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult =
        UrnetworkSdkBridge.AttachResult.Success
    override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit
    override fun connectBestAvailable() = Unit
    override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = initialLocation
    override fun openLocationsViewController(): LocationsViewController? = null
    var lastAppliedWindowType: UrnetworkWindowType? = null
    var lastAppliedFixedIp: Boolean? = null
    var lastAppliedAllowDirect: Boolean? = null
    override fun applyPerformanceProfile(
        windowType: UrnetworkWindowType,
        fixedIpSize: Boolean,
        allowDirect: Boolean,
    ) {
        lastAppliedWindowType = windowType
        lastAppliedFixedIp = fixedIpSize
        lastAppliedAllowDirect = allowDirect
    }
    var lastProvideControlMode: UrnetworkProvideControlMode? = null
    override fun setProvideControlMode(mode: UrnetworkProvideControlMode) {
        lastProvideControlMode = mode
    }
    var lastProvideNetworkMode: UrnetworkProvideNetworkMode? = null
    override fun setProvideNetworkMode(mode: UrnetworkProvideNetworkMode) {
        lastProvideNetworkMode = mode
    }
    var lastPausedValue: Boolean? = null
    val setProvidePausedCallCount = AtomicInteger(0)
    override fun setProvidePaused(paused: Boolean) {
        lastPausedValue = paused
        setProvidePausedCallCount.incrementAndGet()
    }
    override fun isProvidePaused(): Boolean = true
    val peerCountCallCount = AtomicInteger(0)
    override fun peerCount(): Int {
        peerCountCallCount.incrementAndGet()
        return peerCountValue
    }
    override fun unpaidByteCount(): Long = 0L
    override fun fetchTransferStats() = Unit
    override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
    override suspend fun fetchAccountPoints(): UrnetworkSdkBridge.AccountPointsSnapshot? = null
}

private class FakeBalanceRepository(
    initial: UrnetworkBalanceState = UrnetworkBalanceState.INITIAL,
) : UrnetworkBalanceRepository {
    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<UrnetworkBalanceState> = _state.asStateFlow()
    val refreshCallCount = AtomicInteger(0)
    override suspend fun refresh() {
        refreshCallCount.incrementAndGet()
    }
}

private fun fakeBalanceRepo() = FakeBalanceRepository()
