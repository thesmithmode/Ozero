package ru.ozero.app.ui.settings.engines

import com.bringyour.sdk.ConnectLocation
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

    @Test
    fun `uiState стартует как Loading`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(FakeUrnetworkBridge(), FakeSettingsRepo(), FakeUrnetworkConfigStore())
        assertSame(UrnetworkSettingsUiState.Loading, vm.uiState.value)
    }

    @Test
    fun `uiState переходит в NotConnected когда bridge не подключён`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(
            FakeUrnetworkBridge(connected = false),
            FakeSettingsRepo(),
            FakeUrnetworkConfigStore(),
        )
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.NotConnected>(vm.uiState.value)
    }

    @Test
    fun `subscriptionBalance стартует с null когда никто не подписан`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(FakeUrnetworkBridge(), FakeSettingsRepo(), FakeUrnetworkConfigStore())
        assertNull(vm.subscriptionBalance.value)
    }

    @Test
    fun `subscriptionBalance отдаёт snapshot из bridge при первой подписке`() = runTest {
        val snap = UrnetworkSdkBridge.SubscriptionBalanceSnapshot(
            balanceBytes = 7_000_000L,
            pendingBytes = 100_000L,
            startBalanceBytes = 10_000_000L,
            usedBytes = 2_900_000L,
            plan = "Supporter",
            store = "google",
        )
        val bridge = FakeUrnetworkBridge(subscriptionBalance = snap)
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), FakeUrnetworkConfigStore())
        val first = vm.subscriptionBalance.first { it != null }
        assertEquals(snap, first)
    }

    @Test
    fun `subscriptionBalance polling вызывает bridge повторно`() = runTest {
        val callCount = AtomicInteger(0)
        val snap = UrnetworkSdkBridge.SubscriptionBalanceSnapshot(0L, 0L, 1L, 1L, null, null)
        val bridge = FakeUrnetworkBridge(
            subscriptionBalance = snap,
            balanceCallCounter = callCount,
        )
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), FakeUrnetworkConfigStore())
        val collector = backgroundScope.launch { vm.subscriptionBalance.collect {} }
        vm.subscriptionBalance.first { it != null }
        runCurrent()
        val before = callCount.get()
        advanceTimeBy(60_001L)
        runCurrent()
        val after = callCount.get()
        collector.cancel()
        assertEquals(
            true,
            after > before,
            "Polling должен вызвать bridge ещё раз через 60s; before=$before after=$after",
        )
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
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store)
        vm.selectWindowType(UrnetworkWindowType.SPEED)
        advanceUntilIdle()
        assertEquals(UrnetworkWindowType.SPEED, store.windowType().first())
        assertEquals(UrnetworkWindowType.SPEED, bridge.lastWindowType)
    }

    @Test
    fun `selectWindowType AUTO сбрасывает fixedIpSize`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = FakeUrnetworkConfigStore()
        store.setFixedIpSize(true)
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store)
        vm.selectWindowType(UrnetworkWindowType.AUTO)
        advanceUntilIdle()
        assertEquals(false, store.fixedIpSize().first())
    }

    @Test
    fun `toggleFixedIpSize сохраняет в configStore и применяет profile`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val store = FakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), store)
        vm.toggleFixedIpSize(true)
        advanceUntilIdle()
        assertEquals(true, store.fixedIpSize().first())
        assertEquals(true, bridge.lastFixedIp)
    }

    @Test
    fun `subscriptionBalance остаётся null когда bridge возвращает null (free user)`() = runTest {
        val bridge = FakeUrnetworkBridge(subscriptionBalance = null)
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), FakeUrnetworkConfigStore())
        vm.subscriptionBalance.first()
        runCurrent()
        assertNull(vm.subscriptionBalance.value)
    }

    @Test
    fun `selectWindowType сохраняет выбранный тип в configStore`() = runTest {
        val store = FakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(FakeUrnetworkBridge(), FakeSettingsRepo(), store)
        vm.selectWindowType(UrnetworkWindowType.SPEED)
        advanceUntilIdle()
        assertEquals(UrnetworkWindowType.SPEED, store.windowType().first())
    }

    @Test
    fun `selectWindowType вызывает applyPerformanceProfile на bridge`() = runTest {
        val bridge = FakeUrnetworkBridge()
        val vm = UrnetworkEngineSettingsViewModel(bridge, FakeSettingsRepo(), FakeUrnetworkConfigStore())
        vm.selectWindowType(UrnetworkWindowType.QUALITY)
        advanceUntilIdle()
        assertEquals(UrnetworkWindowType.QUALITY, bridge.lastAppliedWindowType)
    }

    @Test
    fun `selectWindowType AUTO сбрасывает fixedIpSize в false в configStore`() = runTest {
        val store = FakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(FakeUrnetworkBridge(), FakeSettingsRepo(), store)
        vm.toggleFixedIpSize(true)
        advanceUntilIdle()
        vm.selectWindowType(UrnetworkWindowType.AUTO)
        advanceUntilIdle()
        assertEquals(false, store.fixedIpSize().first())
    }

    @Test
    fun `toggleFixedIpSize сохраняет значение в configStore`() = runTest {
        val store = FakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(FakeUrnetworkBridge(), FakeSettingsRepo(), store)
        vm.toggleFixedIpSize(true)
        advanceUntilIdle()
        assertEquals(true, store.fixedIpSize().first())
    }

    @Test
    fun `windowType StateFlow отражает начальное значение из configStore`() = runTest {
        val store = FakeUrnetworkConfigStore()
        val vm = UrnetworkEngineSettingsViewModel(FakeUrnetworkBridge(), FakeSettingsRepo(), store)
        advanceUntilIdle()
        assertEquals(UrnetworkWindowType.AUTO, vm.windowType.value)
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

private class FakeUrnetworkBridge(
    private val connected: Boolean = false,
    private val subscriptionBalance: UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null,
    private val balanceCallCounter: AtomicInteger? = null,
) : UrnetworkSdkBridge {
    @Volatile var lastWindowType: UrnetworkWindowType? = null
    @Volatile var lastFixedIp: Boolean? = null
    override fun applyPerformanceProfile(windowType: UrnetworkWindowType, fixedIpSize: Boolean) {
        lastWindowType = windowType
        lastFixedIp = fixedIpSize
    }
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
    override fun connectTo(location: ConnectLocation) = Unit
    override fun connectBestAvailable() = Unit
    override fun selectedLocation(): ConnectLocation? = null
    override fun openLocationsViewController(): LocationsViewController? = null
    var lastAppliedWindowType: UrnetworkWindowType? = null
    var lastAppliedFixedIp: Boolean? = null
    override fun applyPerformanceProfile(windowType: UrnetworkWindowType, fixedIpSize: Boolean) {
        lastAppliedWindowType = windowType
        lastAppliedFixedIp = fixedIpSize
    }
    override fun setProvidePaused(paused: Boolean) = Unit
    override fun isProvidePaused(): Boolean = true
    override fun peerCount(): Int = 0
    override fun unpaidByteCount(): Long = 0L
    override fun fetchTransferStats() = Unit
    override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? {
        balanceCallCounter?.incrementAndGet()
        return subscriptionBalance
    }
}
