package ru.ozero.app.ui.settings.engines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.app.urnetwork.UrnetworkBalanceRepository
import ru.ozero.app.urnetwork.UrnetworkBalanceState
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UrnetworkSharedTrafficViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var bridge: FakeBridge
    private lateinit var balanceRepo: FakeBalanceRepository
    private lateinit var vm: UrnetworkSharedTrafficViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        bridge = FakeBridge()
        balanceRepo = FakeBalanceRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `после init показывает unpaidByteCount из bridge`() = runTest(dispatcher) {
        bridge.unpaidBytes = 123_456_789L
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        advanceUntilIdle()
        assertEquals(123_456_789L, vm.unpaidBytes.value)
    }

    @Test
    fun `isLoading false после загрузки`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        advanceUntilIdle()
        assertEquals(false, vm.isLoading.value)
    }

    @Test
    fun `refresh вызывает fetchTransferStats повторно`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        advanceUntilIdle()
        val callsBefore = bridge.fetchTransferStatsCalls
        vm.refresh()
        advanceUntilIdle()
        assertEquals(callsBefore + 1, bridge.fetchTransferStatsCalls)
    }

    @Test
    fun `unpaidBytes 0 при старте`() = runTest(dispatcher) {
        bridge.unpaidBytes = 0L
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        advanceUntilIdle()
        assertEquals(0L, vm.unpaidBytes.value)
    }

    @Test
    fun `unpaidBytes обновляется после refresh`() = runTest(dispatcher) {
        bridge.unpaidBytes = 1_000_000L
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        advanceUntilIdle()
        bridge.unpaidBytes = 2_000_000L
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2_000_000L, vm.unpaidBytes.value)
    }

    @Test
    fun `init вызывает balance refresh сразу`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        advanceUntilIdle()
        assertTrue(balanceRepo.refreshCalls >= 1)
    }

    @Test
    fun `balance refresh периодически вызывается через polling`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        advanceUntilIdle()
        val initial = balanceRepo.refreshCalls
        advanceTimeBy(35_000L)
        advanceUntilIdle()
        assertTrue(balanceRepo.refreshCalls > initial)
    }

    @Test
    fun `refresh вызывает balance refresh`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        advanceUntilIdle()
        val before = balanceRepo.refreshCalls
        vm.refresh()
        advanceUntilIdle()
        assertTrue(balanceRepo.refreshCalls > before)
    }

    @Test
    fun `balanceState проксирует state из repository`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        advanceUntilIdle()
        assertEquals(UrnetworkBalanceState.INITIAL, vm.balanceState.value)
    }

    private class FakeBalanceRepository : UrnetworkBalanceRepository {
        private val _state = MutableStateFlow(UrnetworkBalanceState.INITIAL)
        override val state: StateFlow<UrnetworkBalanceState> = _state
        var refreshCalls = 0
        override suspend fun refresh() {
            refreshCalls++
        }
    }

    private class FakeBridge : UrnetworkSdkBridge {
        var unpaidBytes = 0L
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
        override suspend fun fetchSubscriptionBalance() = null
    }
}
