package ru.ozero.app.ui.settings.engines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class UrnetworkSharedTrafficViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var bridge: FakeBridge
    private lateinit var vm: UrnetworkSharedTrafficViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        bridge = FakeBridge()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `после init показывает unpaidByteCount из bridge`() = runTest(dispatcher) {
        bridge.unpaidBytes = 123_456_789L
        vm = UrnetworkSharedTrafficViewModel(bridge)
        advanceUntilIdle()
        assertEquals(123_456_789L, vm.unpaidBytes.value)
    }

    @Test
    fun `после init показывает plan из subscriptionBalance`() = runTest(dispatcher) {
        bridge.plan = "pro"
        vm = UrnetworkSharedTrafficViewModel(bridge)
        advanceUntilIdle()
        assertEquals("pro", vm.plan.value)
    }

    @Test
    fun `plan null если fetchSubscriptionBalance возвращает null`() = runTest(dispatcher) {
        bridge.balanceResult = null
        vm = UrnetworkSharedTrafficViewModel(bridge)
        advanceUntilIdle()
        assertNull(vm.plan.value)
    }

    @Test
    fun `isLoading false после загрузки`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge)
        advanceUntilIdle()
        assertEquals(false, vm.isLoading.value)
    }

    @Test
    fun `refresh вызывает fetchTransferStats повторно`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge)
        advanceUntilIdle()
        val callsBefore = bridge.fetchTransferStatsCalls
        vm.refresh()
        advanceUntilIdle()
        assertEquals(callsBefore + 1, bridge.fetchTransferStatsCalls)
    }

    @Test
    fun `после init показывает balanceBytes из subscriptionBalance`() = runTest(dispatcher) {
        bridge.plan = "free"
        bridge.balanceBytes = 1_000_000_000L
        vm = UrnetworkSharedTrafficViewModel(bridge)
        advanceUntilIdle()
        assertEquals(1_000_000_000L, vm.balanceBytes.value)
    }

    @Test
    fun `balanceBytes равен 0 если fetchSubscriptionBalance возвращает null`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge)
        advanceUntilIdle()
        assertEquals(0L, vm.balanceBytes.value)
    }

    @Test
    fun `unpaidBytes показывает ПРЕДОСТАВЛЕННЫЙ трафик а не потреблённый из subscriptionBalance`() =
        runTest(dispatcher) {
            bridge.unpaidBytes = 50_000_000L
            bridge.plan = "free"
            vm = UrnetworkSharedTrafficViewModel(bridge)
            advanceUntilIdle()
            assertEquals(
                50_000_000L,
                vm.unpaidBytes.value,
                "Расшаренный трафик = unpaidByteCount (предоставлено другим), " +
                    "не usedBytes из subscriptionBalance (это потребление).",
            )
        }

    private class FakeBridge : UrnetworkSdkBridge {
        var unpaidBytes = 0L
        var plan: String? = null
        var balanceBytes = 0L
        var balanceResult: UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
            get() = if (plan != null || field != null) {
                UrnetworkSdkBridge.SubscriptionBalanceSnapshot(
                    balanceBytes = balanceBytes, pendingBytes = 0L, startBalanceBytes = 0L,
                    usedBytes = 0L, plan = plan, store = null,
                )
            } else {
                null
            }
        var fetchTransferStatsCalls = 0

        override suspend fun start(walletAddress: String, apiUrl: String, connectUrl: String, byClientJwt: String) =
            UrnetworkSdkBridge.StartResult.Success
        override suspend fun stop() = Unit
        override fun isRunning() = false
        override suspend fun attachTun(tunFd: Int) = UrnetworkSdkBridge.AttachResult.Success
        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit
        override fun connectBestAvailable() = Unit
        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = null
        override fun openLocationsViewController(): com.bringyour.sdk.LocationsViewController? = null
        override fun setProvidePaused(paused: Boolean) = Unit
        override fun isProvidePaused() = false
        override fun peerCount() = 0
        override fun unpaidByteCount(): Long = unpaidBytes
        override fun fetchTransferStats() {
            fetchTransferStatsCalls++
        }
        override suspend fun fetchSubscriptionBalance() = balanceResult
        var accountPointsResult: UrnetworkSdkBridge.AccountPointsSnapshot? = null
        override suspend fun fetchAccountPoints() = accountPointsResult
    }

    @Test
    fun `accountPoints null если bridge возвращает null`() = runTest(dispatcher) {
        bridge.accountPointsResult = null
        vm = UrnetworkSharedTrafficViewModel(bridge)
        advanceUntilIdle()
        assertNull(vm.accountPoints.value)
    }

    @Test
    fun `accountPoints заполняется из bridge`() = runTest(dispatcher) {
        bridge.accountPointsResult = UrnetworkSdkBridge.AccountPointsSnapshot(
            totalPoints = 1500.0,
            payoutPoints = 1000.0,
            referralPoints = 200.0,
            reliabilityPoints = 300.0,
            multiplierPoints = 0.0,
        )
        vm = UrnetworkSharedTrafficViewModel(bridge)
        advanceUntilIdle()
        val pts = vm.accountPoints.value
        assertEquals(1500.0, pts?.totalPoints)
        assertEquals(1000.0, pts?.payoutPoints)
    }
}
