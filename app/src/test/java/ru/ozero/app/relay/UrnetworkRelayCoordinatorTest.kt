package ru.ozero.app.relay

import io.mockk.any
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.TunnelController
import ru.ozero.commonvpn.TunnelState
import ru.ozero.engineurnetwork.InMemoryUrnetworkConfigStore
import ru.ozero.engineurnetwork.UrnetworkConfig
import ru.ozero.engineurnetwork.UrnetworkJwtBootstrapper
import ru.ozero.engineurnetwork.UrnetworkProvideControlMode
import ru.ozero.engineurnetwork.UrnetworkProvideNetworkMode
import ru.ozero.engineurnetwork.UrnetworkSdkBridge
import ru.ozero.engineurnetwork.setByClientJwt
import ru.ozero.engineurnetwork.setProvideNetworkMode
import ru.ozero.enginescore.EngineId
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class UrnetworkRelayCoordinatorTest {

    private lateinit var coordinatorScope: CoroutineScope

    private lateinit var tunnelStateFlow: MutableStateFlow<TunnelState>
    private lateinit var configStore: InMemoryUrnetworkConfigStore
    private lateinit var bridge: FakeBridge
    private lateinit var bootstrapper: FakeJwtBootstrapper
    private lateinit var tunnelController: TunnelController
    private lateinit var coordinator: UrnetworkRelayCoordinator

    @BeforeEach
    fun setUp() {
        tunnelStateFlow = MutableStateFlow(TunnelState.Idle)
        configStore = InMemoryUrnetworkConfigStore(UrnetworkConfig(walletOverride = "test-wallet"))
        bridge = FakeBridge()
        bootstrapper = FakeJwtBootstrapper()

        tunnelController = mockk()
        every { tunnelController.state } returns tunnelStateFlow
    }

    @AfterEach
    fun tearDown() {
        if (::coordinator.isInitialized) {
            coordinator.stop()
        }
    }

    private fun setByClientJwt(value: String?) {
        configStore.inject { it.copy(byClientJwt = value) }
    }

    private fun relayTest(
        networkMonitor: RelayNetworkMonitor? = null,
        relayLockManager: RelayLockManager? = null,
        pipeFactory: DummyPipeFactory = FakePipeFactory(),
        block: suspend TestScope.() -> Unit,
    ) = runTest(UnconfinedTestDispatcher()) {
        coordinatorScope = backgroundScope
        coordinator = UrnetworkRelayCoordinator(
            bridge = bridge,
            configStore = configStore,
            tunnelController = tunnelController,
            jwtBootstrapper = bootstrapper,
            networkMonitor = networkMonitor,
            relayLockManager = relayLockManager,
            pipeFactory = pipeFactory,
            scope = coordinatorScope,
        )
        coordinator.start()
        try {
            block()
        } finally {
            coordinator.stop()
        }
    }

    @Test
    fun `relay запускается для ByeDPI когда JWT есть`() = relayTest {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        assertEquals(1, bridge.startCalls)
        assertEquals(false, bridge.lastProvidePaused)
    }

    @Test
    fun `relay запускается для WARP когда JWT есть`() = relayTest {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.WARP, socksPort = 0)

        assertEquals(1, bridge.startCalls)
        assertEquals(false, bridge.lastProvidePaused)
    }

    @Test
    fun `relay не запускает bridge и не трогает setProvidePaused для URnetwork движка`() = relayTest {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)

        assertEquals(0, bridge.startCalls)
        assertEquals(0, bridge.setProvidePausedCalls)
    }

    @Test
    fun `relay передаёт setProvidePaused false когда provideEnabled true в configStore`() = relayTest {
        setByClientJwt("test-jwt")
        configStore.update { it.copy(provideEnabled = true) }
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        assertEquals(1, bridge.startCalls)
        assertEquals(false, bridge.lastProvidePaused)
    }

    @Test
    fun `relay передаёт setProvidePaused true когда configStore был выключен`() = relayTest {
        setByClientJwt("test-jwt")
        configStore.update { it.copy(provideEnabled = false) }
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        assertEquals(1, bridge.startCalls)
        assertEquals(true, bridge.lastProvidePaused)
    }

    @Test
    fun `relay не запускает bridge если JWT null но триггерит bootstrap`() = relayTest {
        setByClientJwt(null)
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        assertEquals(0, bridge.startCalls, "без JWT bridge.start не зовётся пока bootstrap не закончит")
        assertEquals(0, bridge.setProvidePausedCalls)
        assertEquals(1, bootstrapper.calls, "missing JWT при не-URnetwork engine → bootstrapper вызван")
    }

    @Test
    fun `bootstrap acquires JWT и затем relay стартует когда JWT появился через configStore`() = relayTest {
        bootstrapper.onCallSetJwt("bootstrapped-jwt", configStore)
        setByClientJwt(null)

        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        assertEquals(1, bootstrapper.calls, "bootstrap вызван")
        assertEquals(1, bridge.startCalls, "после acquire через configStore distinct emit → bridge.start")
    }

    @Test
    fun `bootstrap не зовётся повторно в одной tunnel session`() = relayTest {
        setByClientJwt(null)
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        assertEquals(1, bootstrapper.calls, "не thrash: один acquire на session")
    }

    @Test
    fun `bootstrap session flag сбрасывается на disconnect — позволяет retry на следующем reconnect`() =
        relayTest {
            setByClientJwt(null)
            tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)
            assertEquals(1, bootstrapper.calls)

            tunnelStateFlow.value = TunnelState.Idle
            tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

            assertEquals(2, bootstrapper.calls, "новая session после disconnect → retry разрешён")
        }

    @Test
    fun `bootstrap не зовётся когда URnetwork engine активен — он сам acquires`() = relayTest {
        setByClientJwt(null)
        tunnelStateFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)

        assertEquals(0, bootstrapper.calls, "URnetwork engine.start сам делает acquire — coordinator не дублирует")
    }

    @Test
    fun `bootstrap не зовётся когда JWT уже есть`() = relayTest {
        setByClientJwt("existing-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        assertEquals(0, bootstrapper.calls, "JWT уже есть → coordinator стартует bridge без bootstrap")
        assertEquals(1, bridge.startCalls)
    }

    @Test
    fun `relay останавливает bridge при Idle если был owned`() = relayTest {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)
        assertEquals(1, bridge.startCalls)

        bridge.running = true
        tunnelStateFlow.value = TunnelState.Idle

        assertEquals(1, bridge.stopCalls)
    }

    @Test
    fun `relay не останавливает bridge при Idle если не был owned`() = relayTest {
        tunnelStateFlow.value = TunnelState.Idle

        assertEquals(0, bridge.stopCalls)
    }

    @Test
    fun `relay ownership сбрасывается когда URnetwork становится активным`() = relayTest {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)
        assertEquals(1, bridge.startCalls)

        bridge.running = true
        tunnelStateFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)

        assertEquals(0, bridge.stopCalls, "URnetwork engine owns bridge — coordinator не должен останавливать")
    }

    @Test
    fun `relay не вызывает consumer connect после успешного start`() = relayTest {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        assertEquals(0, bridge.connectBestAvailableCalls)
    }

    @Test
    fun `connectBestAvailable не вызывается при failed start`() = relayTest {
        bridge.startResult = UrnetworkSdkBridge.StartResult.Failed("test")
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        advanceTimeBy(35_000L)
        runCurrent()

        assertEquals(0, bridge.connectBestAvailableCalls)
        assertEquals(3, bridge.startCalls, "retry 3 attempts")
    }

    @Test
    fun `relay перезапускается при смене с URnetwork на ByeDPI`() = relayTest {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.URNETWORK, socksPort = 0)
        assertEquals(0, bridge.startCalls)

        tunnelStateFlow.value = TunnelState.Idle
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        assertEquals(1, bridge.startCalls)
        assertEquals(0, bridge.connectBestAvailableCalls)
        assertEquals(1, bridge.attachRelayTunCalls)
    }

    @Test
    fun `relay при смене WARP на ByeDPI перезапускается без consumer connect`() = relayTest {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.WARP, socksPort = 0)
        assertEquals(1, bridge.startCalls)
        assertEquals(0, bridge.connectBestAvailableCalls)
        assertEquals(1, bridge.attachRelayTunCalls)

        bridge.running = true
        tunnelStateFlow.value = TunnelState.Idle
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        assertEquals(2, bridge.startCalls)
        assertEquals(0, bridge.connectBestAvailableCalls)
        assertEquals(2, bridge.attachRelayTunCalls)
    }

    @Test
    fun `relay attach dummy IoLoop после успешного start`() = relayTest {
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.WARP, socksPort = 0)

        assertEquals(1, bridge.startCalls)
        assertEquals(0, bridge.attachTunCalls, "relay dummy IoLoop не должен идти через consumer attachTun")
        assertEquals(1, bridge.attachRelayTunCalls, "dummy IoLoop должен быть создан после start")
    }

    @Test
    fun `relay не attach dummy IoLoop при failed start`() = relayTest {
        bridge.startResult = UrnetworkSdkBridge.StartResult.Failed("test")
        setByClientJwt("test-jwt")
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        advanceTimeBy(35_000L)
        runCurrent()

        assertEquals(0, bridge.attachRelayTunCalls)
    }

    @Test
    fun `relay applies provide control and network modes from config`() = relayTest {
        setByClientJwt("test-jwt")
        configStore.update { it.copy(provideControlMode = UrnetworkProvideControlMode.AUTO) }
        configStore.setProvideNetworkMode(UrnetworkProvideNetworkMode.ALL)
        tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

        assertEquals(UrnetworkProvideControlMode.AUTO, bridge.lastControlMode)
        assertEquals(UrnetworkProvideNetworkMode.ALL, bridge.lastNetworkMode)
    }

    @Test
    fun `relay starts monitor and acquires lock with always-on provide`() {
        val monitor = mockk<RelayNetworkMonitor>(relaxed = true)
        val locks = mockk<RelayLockManager>(relaxed = true)
        relayTest(networkMonitor = monitor, relayLockManager = locks) {
            setByClientJwt("test-jwt")
            configStore.update { it.copy(provideEnabled = true) }
            configStore.setProvideNetworkMode(UrnetworkProvideNetworkMode.ALL)
            tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

            verify { monitor.start(UrnetworkProvideNetworkMode.ALL) }
            verify { locks.acquire() }
        }
    }

    @Test
    fun `relay skips monitor and lock when config disables provide`() {
        val monitor = mockk<RelayNetworkMonitor>(relaxed = true)
        val locks = mockk<RelayLockManager>(relaxed = true)
        relayTest(networkMonitor = monitor, relayLockManager = locks) {
            setByClientJwt("test-jwt")
            configStore.update { it.copy(provideEnabled = false) }
            tunnelStateFlow.value = TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080)

            verify(exactly = 0) { monitor.start(any()) }
            verify(exactly = 0) { locks.acquire() }
        }
    }

    @Test
    fun `relay closes dummy pipe when attach fails`() {
        val pipe = RecordingPipe()
        bridge.attachResult = UrnetworkSdkBridge.AttachResult.Failed("attach failed")
        relayTest(pipeFactory = FakePipeFactory(pipe)) {
            setByClientJwt("test-jwt")
            tunnelStateFlow.value = TunnelState.Connected(EngineId.WARP, socksPort = 0)

            assertEquals(1, bridge.attachRelayTunCalls)
            assertEquals(1, pipe.closeCalls)
        }
    }

    @Test
    fun `watchdog stops unhealthy owned relay and releases resources`() {
        val monitor = mockk<RelayNetworkMonitor>(relaxed = true)
        val locks = mockk<RelayLockManager>(relaxed = true)
        relayTest(networkMonitor = monitor, relayLockManager = locks) {
            setByClientJwt("test-jwt")
            bridge.diagnosticsResult = "device=null"
            tunnelStateFlow.value = TunnelState.Connected(EngineId.WARP, socksPort = 0)

            advanceTimeBy(60_000L)
            runCurrent()

            assertEquals(1, bridge.stopCalls)
            verify { monitor.stop() }
            verify { locks.release() }
        }
    }

    private class FakeJwtBootstrapper : UrnetworkJwtBootstrapper {
        var calls: Int = 0
        private var onCallAction: (suspend () -> UrnetworkJwtBootstrapper.Result)? = null

        fun onCallSetJwt(jwt: String, store: InMemoryUrnetworkConfigStore) {
            onCallAction = {
                store.setByClientJwt(jwt)
                UrnetworkJwtBootstrapper.Result.Acquired
            }
        }

        override suspend fun ensureClientJwt(): UrnetworkJwtBootstrapper.Result {
            calls++
            return onCallAction?.invoke()
                ?: UrnetworkJwtBootstrapper.Result.Failed("FakeJwtBootstrapper default failure")
        }
    }

    private class FakePipeFactory(
        private val writeEnd: AutoCloseable = AutoCloseable {},
    ) : DummyPipeFactory {
        override fun create() = DummyPipeFactory.PipeHandle(42, writeEnd)
    }

    private class RecordingPipe : AutoCloseable {
        var closeCalls = 0
        override fun close() {
            closeCalls++
        }
    }

    private class FakeBridge : UrnetworkSdkBridge {
        var startCalls = 0
        var stopCalls = 0
        var setProvidePausedCalls = 0
        var connectBestAvailableCalls = 0
        var lastProvidePaused: Boolean? = null
        var running = false
        var startResult: UrnetworkSdkBridge.StartResult = UrnetworkSdkBridge.StartResult.Success
        var attachResult: UrnetworkSdkBridge.AttachResult = UrnetworkSdkBridge.AttachResult.Success
        var attachTunCalls = 0
        var attachRelayTunCalls = 0
        var diagnosticsResult: String = "running=true"
        var lastControlMode: UrnetworkProvideControlMode? = null
        var lastNetworkMode: UrnetworkProvideNetworkMode? = null

        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byClientJwt: String,
        ): UrnetworkSdkBridge.StartResult {
            startCalls++
            return startResult
        }

        override suspend fun stop() {
            stopCalls++
        }
        override fun isRunning() = running
        override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult {
            attachTunCalls++
            return attachResult
        }
        override suspend fun attachRelayTun(tunFd: Int): UrnetworkSdkBridge.AttachResult {
            attachRelayTunCalls++
            return attachResult
        }
        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit
        override fun connectBestAvailable() {
            connectBestAvailableCalls++
        }
        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = null
        override fun openLocationsViewController(): com.bringyour.sdk.LocationsViewController? = null
        override fun setProvidePaused(paused: Boolean) {
            setProvidePausedCalls++
            lastProvidePaused = paused
        }
        override fun setProvideControlMode(mode: UrnetworkProvideControlMode) {
            lastControlMode = mode
        }
        override fun setProvideNetworkMode(mode: UrnetworkProvideNetworkMode) {
            lastNetworkMode = mode
        }
        override fun isProvidePaused() = false
        override fun peerCount() = 0
        override fun unpaidByteCount() = 0L
        override fun fetchTransferStats() = Unit
        override suspend fun fetchSubscriptionBalance() = null
        override fun relayDiagnostics() = diagnosticsResult
    }
}
