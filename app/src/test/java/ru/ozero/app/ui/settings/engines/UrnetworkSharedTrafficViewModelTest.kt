package ru.ozero.app.ui.settings.engines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
    fun `подписка на balanceState запускает первый refresh`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        val collectJob = backgroundScope.launch { vm.balanceState.collect {} }
        advanceUntilIdle()
        assertTrue(balanceRepo.refreshCalls >= 1)
        collectJob.cancel()
    }

    @Test
    fun `balance refresh периодически через poll interval пока подписаны`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        val collectJob = backgroundScope.launch { vm.balanceState.collect {} }
        advanceUntilIdle()
        val afterFirst = balanceRepo.refreshCalls
        advanceTimeBy(30_001L)
        advanceUntilIdle()
        assertTrue(balanceRepo.refreshCalls > afterFirst, "ожидался второй refresh после 30s, got=${balanceRepo.refreshCalls}, was=$afterFirst")
        advanceTimeBy(30_001L)
        advanceUntilIdle()
        assertTrue(balanceRepo.refreshCalls >= afterFirst + 2)
        collectJob.cancel()
    }

    @Test
    fun `без подписки на balanceState polling не идёт`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        advanceUntilIdle()
        advanceTimeBy(60_000L)
        advanceUntilIdle()
        assertEquals(0, balanceRepo.refreshCalls)
    }

    @Test
    fun `отмена подписки останавливает polling через keepalive`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        val collectJob = backgroundScope.launch { vm.balanceState.collect {} }
        advanceUntilIdle()
        val whileSubscribed = balanceRepo.refreshCalls
        collectJob.cancel()
        advanceTimeBy(6_000L)
        advanceUntilIdle()
        val afterKeepalive = balanceRepo.refreshCalls
        advanceTimeBy(60_000L)
        advanceUntilIdle()
        assertEquals(afterKeepalive, balanceRepo.refreshCalls, "polling должен остановиться после keepalive, was=$whileSubscribed afterKA=$afterKeepalive final=${balanceRepo.refreshCalls}")
    }

    @Test
    fun `refresh вызывает balance refresh независимо от подписки`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        advanceUntilIdle()
        val before = balanceRepo.refreshCalls
        vm.refresh()
        advanceUntilIdle()
        assertTrue(balanceRepo.refreshCalls > before)
    }

    @Test
    fun `balanceState INITIAL до первого refresh`() = runTest(dispatcher) {
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        advanceUntilIdle()
        assertEquals(UrnetworkBalanceState.INITIAL, vm.balanceState.value)
    }

    @Test
    fun `refresh внутри ticker не валит loop при throw`() = runTest(dispatcher) {
        balanceRepo.throwOnRefresh = true
        vm = UrnetworkSharedTrafficViewModel(bridge, balanceRepo)
        val collectJob = backgroundScope.launch { vm.balanceState.collect {} }
        advanceUntilIdle()
        val first = balanceRepo.refreshCalls
        advanceTimeBy(30_001L)
        advanceUntilIdle()
        assertTrue(balanceRepo.refreshCalls > first, "ticker должен продолжать polling после исключения, got=${balanceRepo.refreshCalls}")
        collectJob.cancel()
    }

    private class FakeBalanceRepository : UrnetworkBalanceRepository {
        private val _state = MutableStateFlow(UrnetworkBalanceState.INITIAL)
        override val state: StateFlow<UrnetworkBalanceState> = _state
        var refreshCalls = 0
        var throwOnRefresh = false
        override suspend fun refresh() {
            refreshCalls++
            if (throwOnRefresh) throw RuntimeException("test-fail")
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
