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
    fun `unpaidBytes 0 при старте`() = runTest(dispatcher) {
        bridge.unpaidBytes = 0L
        vm = UrnetworkSharedTrafficViewModel(bridge)
        advanceUntilIdle()
        assertEquals(0L, vm.unpaidBytes.value)
    }

    @Test
    fun `unpaidBytes обновляется после refresh`() = runTest(dispatcher) {
        bridge.unpaidBytes = 1_000_000L
        vm = UrnetworkSharedTrafficViewModel(bridge)
        advanceUntilIdle()
        bridge.unpaidBytes = 2_000_000L
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2_000_000L, vm.unpaidBytes.value)
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
