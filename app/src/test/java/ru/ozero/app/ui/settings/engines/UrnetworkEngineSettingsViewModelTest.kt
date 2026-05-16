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
import ru.ozero.commonvpn.TunnelController
import ru.ozero.enginescore.EngineId
import ru.ozero.engineurnetwork.UrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.UrnetworkWindowType
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
            FakeUrnetworkConfigStore(),
            idleTunnel(),
        )
        assertSame(UrnetworkSettingsUiState.Loading, vm.uiState.value)
    }

    @Test
    fun `uiState переходит в NotConnected когда engine не активен`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(
            FakeUrnetworkBridge(connected = false),
            FakeSettingsRepo(),
            FakeUrnetworkConfigStore(),
            idleTunnel(),
        )
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.NotConnected>(vm.uiState.value)
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
        val store = FakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, activeTunnel())
        vm.selectWindowType(UrnetworkWindowType.SPEED)
        advanceUntilIdle()
        assertEquals(UrnetworkWindowType.SPEED, store.windowType().first())
        assertEquals(UrnetworkWindowType.SPEED, bridge.lastAppliedWindowType)
    }

    @Test
    fun `selectWindowType AUTO сбрасывает fixedIpSize`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = FakeUrnetworkConfigStore()
        store.setFixedIpSize(true)
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, activeTunnel())
        vm.selectWindowType(UrnetworkWindowType.AUTO)
        advanceUntilIdle()
        assertEquals(false, store.fixedIpSize().first())
    }

    @Test
    fun `toggleFixedIpSize сохраняет в configStore и применяет profile`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = FakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, activeTunnel())
        vm.toggleFixedIpSize(true)
        advanceUntilIdle()
        assertEquals(true, store.fixedIpSize().first())
        assertEquals(true, bridge.lastAppliedFixedIp)
    }

    @Test
    fun `selectWindowType не вызывает bridge applyPerformanceProfile когда engine не активен`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = FakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store, idleTunnel())
        vm.selectWindowType(UrnetworkWindowType.SPEED)
        advanceUntilIdle()
        assertEquals(UrnetworkWindowType.SPEED, store.windowType().first())
        assertNull(bridge.lastAppliedWindowType)
    }

    @Test
    fun `windowType StateFlow отражает начальное значение из configStore`() = runTest {
        val store = FakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(FakeUrnetworkBridge(), FakeSettingsRepo(), store, idleTunnel())
        advanceUntilIdle()
        assertEquals(UrnetworkWindowType.AUTO, vm.windowType.value)
    }

    @Test
    fun `switchingCountry стартует с false`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(
            FakeUrnetworkBridge(),
            FakeSettingsRepo(),
            FakeUrnetworkConfigStore(),
            idleTunnel(),
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
            FakeUrnetworkConfigStore(),
            activeTunnel(),
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
            FakeUrnetworkConfigStore(),
            activeTunnel(),
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
        val selectBody = source.substringAfter("fun selectLocation(").substringBefore("private fun startSwitchingIndicator")
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
        val filterBody = source.substringAfter("private fun applyFilter(").substringBefore("private fun teardownLocationsVc")
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
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), FakeUrnetworkConfigStore(), activeTunnel())
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
    fun `peerCount остаётся 0 пока engine не активен`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val vm = UrnetworkEngineSettingsViewModel(
            bridge,
            FakeSettingsRepo(),
            FakeUrnetworkConfigStore(),
            idleTunnel(),
        )
        val collector = backgroundScope.launch { vm.peerCount.collect {} }
        advanceTimeBy(10_000L)
        runCurrent()
        collector.cancel()
        assertEquals(0, vm.peerCount.value)
        assertEquals(0, bridge.peerCountCallCount.get())
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
    override suspend fun setCustomDnsServers(servers: List<String>) = Unit
    override suspend fun setHostsMode(mode: ru.ozero.enginescore.settings.HostsMode) = Unit
    override suspend fun setHosts(hosts: List<String>) = Unit
    override suspend fun setUiLocaleTag(tag: String?) = Unit
    override suspend fun setAppMode(mode: ru.ozero.enginescore.settings.AppMode) = Unit
    override suspend fun setKillswitchEnabled(enabled: Boolean) = Unit
    override suspend fun setAlwaysOnBannerDismissed(dismissed: Boolean) = Unit
}

private class FakeUrnetworkConfigStore : UrnetworkConfigStore {
    private val wallet = kotlinx.coroutines.flow.MutableStateFlow("0xWALLET")
    private val byJwt = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val byClientJwt = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val winType = kotlinx.coroutines.flow.MutableStateFlow(UrnetworkWindowType.AUTO)
    private val fixedIp = kotlinx.coroutines.flow.MutableStateFlow(false)
    override fun walletAddress(): kotlinx.coroutines.flow.Flow<String> = wallet
    override fun walletOverride(): kotlinx.coroutines.flow.Flow<String?> = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun setWalletOverride(value: String?) = Unit
    override fun byJwt(): kotlinx.coroutines.flow.Flow<String?> = byJwt
    override suspend fun setByJwt(value: String?) {
        byJwt.value = value
    }
    override fun byClientJwt(): kotlinx.coroutines.flow.Flow<String?> = byClientJwt
    override suspend fun setByClientJwt(value: String?) {
        byClientJwt.value = value
    }
    override fun windowType(): kotlinx.coroutines.flow.Flow<UrnetworkWindowType> = winType
    override suspend fun setWindowType(value: UrnetworkWindowType) {
        winType.value = value
    }
    override fun fixedIpSize(): kotlinx.coroutines.flow.Flow<Boolean> = fixedIp
    override suspend fun setFixedIpSize(value: Boolean) {
        fixedIp.value = value
    }
}

private data class FakeLocationToken(override val countryCode: String?) : UrnetworkSdkBridge.LocationToken

private class FakeUrnetworkBridge(
    private val connected: Boolean = false,
    private val initialLocation: UrnetworkSdkBridge.LocationToken? = null,
    private val peerCountValue: Int = 0,
) : UrnetworkSdkBridge {
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
    override fun applyPerformanceProfile(windowType: UrnetworkWindowType, fixedIpSize: Boolean) {
        lastAppliedWindowType = windowType
        lastAppliedFixedIp = fixedIpSize
    }
    override fun setProvidePaused(paused: Boolean) = Unit
    override fun isProvidePaused(): Boolean = true
    val peerCountCallCount = AtomicInteger(0)
    override fun peerCount(): Int {
        peerCountCallCount.incrementAndGet()
        return peerCountValue
    }
    override fun unpaidByteCount(): Long = 0L
    override fun fetchTransferStats() = Unit
    override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
}
