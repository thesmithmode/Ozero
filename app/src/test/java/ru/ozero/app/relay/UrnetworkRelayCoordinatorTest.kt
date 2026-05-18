package ru.ozero.app.relay

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkConfig
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.enginescore.EngineId
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class UrnetworkRelayCoordinatorTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var coordinatorScope: CoroutineScope

    private lateinit var tunnelStateFlow: MutableStateFlow<TunnelState>
    private lateinit var configStore: InMemoryUrnetworkConfigStore
    private lateinit var bridge: FakeBridge
    private lateinit var coordinator: UrnetworkRelayCoordinator

    @BeforeEach
    fun setUp() {
        coordinatorScope = CoroutineScope(dispatcher + SupervisorJob())
        tunnelStateFlow = MutableStateFlow(TunnelState.Idle)
        configStore = InMemoryUrnetworkConfigStore(UrnetworkConfig(walletOverride = "test-wallet"))
        bridge = FakeBridge()

        val tunnelController = mockk<TunnelController>()
        every { tunnelController.state } returns tunnelStateFlow

        coordinator = UrnetworkRelayCoordinator(bridge, configStore, tunnelController, coordinatorScope)
        coordinator.start()
    }

    @AfterEach
    fun tearDown() {
        coordinator.stop()
        coordinatorScope.cancel()
    }

    private fun setByClientJwt(value: String?) {
        configStore.inject { it.copy(byClientJwt = value) }
    }

    @Test
    fun `relay запускается для ByeDPI когда JWT есть`() = runTest(dispatcher) {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        assertEquals(1, bridge.startCalls)
        assertEquals(false, bridge.lastProvidePaused)
    }

    @Test
    fun `relay запускается для WARP когда JWT есть`() = runTest(dispatcher) {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.WARP, socksPort = 0)

        assertEquals(1, bridge.startCalls)
        assertEquals(false, bridge.lastProvidePaused)
    }

    @Test
    fun `relay не запускает bridge для URnetwork движка — только setProvidePaused`() = runTest(dispatcher) {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)

        assertEquals(0, bridge.startCalls)
        assertEquals(1, bridge.setProvidePausedCalls)
        assertEquals(false, bridge.lastProvidePaused)
    }

    @Test
    fun `relay не запускается если JWT null`() = runTest(dispatcher) {
        setByClientJwt(null)
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        assertEquals(0, bridge.startCalls)
        assertEquals(0, bridge.setProvidePausedCalls)
    }

    @Test
    fun `relay останавливает bridge при Idle если был owned`() = runTest(dispatcher) {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)
        assertEquals(1, bridge.startCalls)

        bridge.running = true
        tunnelStateFlow.value = TunnelState.Idle

        assertEquals(1, bridge.stopCalls)
    }

    @Test
    fun `relay не останавливает bridge при Idle если не был owned`() = runTest(dispatcher) {
        tunnelStateFlow.value = TunnelState.Idle

        assertEquals(0, bridge.stopCalls)
    }

    @Test
    fun `relay ownership сбрасывается когда URnetwork становится активным`() = runTest(dispatcher) {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)
        assertEquals(1, bridge.startCalls)

        bridge.running = true
        tunnelStateFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)

        assertEquals(0, bridge.stopCalls, "URnetwork engine owns bridge — coordinator не должен останавливать")
    }

    @Test
    fun `relay перезапускается при смене с URnetwork на ByeDPI`() = runTest(dispatcher) {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)
        assertEquals(0, bridge.startCalls)

        tunnelStateFlow.value = TunnelState.Idle
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        assertEquals(1, bridge.startCalls)
    }

    private class FakeBridge : UrnetworkSdkBridge {
        var startCalls = 0
        var stopCalls = 0
        var setProvidePausedCalls = 0
        var lastProvidePaused: Boolean? = null
        var running = false

        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byClientJwt: String,
        ): UrnetworkSdkBridge.StartResult {
            startCalls++
            return UrnetworkSdkBridge.StartResult.Success
        }

        override suspend fun stop() {
            stopCalls++
        }
        override fun isRunning() = running
        override suspend fun attachTun(tunFd: Int) = UrnetworkSdkBridge.AttachResult.Success
        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit
        override fun connectBestAvailable() = Unit
        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = null
        override fun openLocationsViewController(): com.bringyour.sdk.LocationsViewController? = null
        override fun setProvidePaused(paused: Boolean) {
            setProvidePausedCalls++
            lastProvidePaused = paused
        }
        override fun isProvidePaused() = false
        override fun peerCount() = 0
        override fun unpaidByteCount() = 0L
        override fun fetchTransferStats() = Unit
        override suspend fun fetchSubscriptionBalance() = null
    }
}
