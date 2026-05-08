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
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
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
        val vm = UrnetworkEngineSettingsViewModel(FakeUrnetworkBridge())
        assertSame(UrnetworkSettingsUiState.Loading, vm.uiState.value)
    }

    @Test
    fun `uiState переходит в NotConnected когда bridge не подключён`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(FakeUrnetworkBridge(connected = false))
        advanceUntilIdle()
        assertIs<UrnetworkSettingsUiState.NotConnected>(vm.uiState.value)
    }

    @Test
    fun `subscriptionBalance стартует с null когда никто не подписан`() = runTest {
        val vm = UrnetworkEngineSettingsViewModel(FakeUrnetworkBridge())
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
        val vm = UrnetworkEngineSettingsViewModel(bridge)
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
        val vm = UrnetworkEngineSettingsViewModel(bridge)
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
    fun `subscriptionBalance остаётся null когда bridge возвращает null (free user)`() = runTest {
        val bridge = FakeUrnetworkBridge(subscriptionBalance = null)
        val vm = UrnetworkEngineSettingsViewModel(bridge)
        vm.subscriptionBalance.first()
        runCurrent()
        assertNull(vm.subscriptionBalance.value)
    }
}

private class FakeUrnetworkBridge(
    private val connected: Boolean = false,
    private val subscriptionBalance: UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null,
    private val balanceCallCounter: AtomicInteger? = null,
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
    override fun connectTo(location: ConnectLocation) = Unit
    override fun connectBestAvailable() = Unit
    override fun selectedLocation(): ConnectLocation? = null
    override fun openLocationsViewController(): LocationsViewController? = null
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
