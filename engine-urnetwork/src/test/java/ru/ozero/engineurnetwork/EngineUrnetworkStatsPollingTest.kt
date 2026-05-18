package ru.ozero.engineurnetwork

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.auth.ClientJwtResult
import ru.ozero.engineurnetwork.auth.GuestJwtResult
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineStats
import ru.ozero.enginescore.Upstream
import kotlin.test.assertEquals

class EngineUrnetworkStatsPollingTest {

    private val baseConfig = EngineConfig.Urnetwork(jwtToken = "")
    private val pollIntervalMs = 1_000L

    @Test
    fun `stats эмитит peerCount после start с не-нулевым числом пиров`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val bridge = FakeBridge(initialPeerCount = 5)
        val engine = EngineUrnetwork(
            configStore = fakeStore(byJwt = "j", byClientJwt = "cj"),
            sdkBridge = bridge,
            authService = FakeAuth(),
            deviceIdentity = null,
            pluginScope = scope,
            statsPollIntervalMs = pollIntervalMs,
        )
        engine.start(baseConfig, Upstream.None)
        runCurrent()
        val stats = engine.stats().first()
        assertEquals(5, stats.activeConnections, "первый poll даёт peerCount=5")
        scope.cancel()
    }

    @Test
    fun `stats обновляется на следующих poll-итерациях при изменении peerCount`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val bridge = FakeBridge(initialPeerCount = 2)
        val engine = EngineUrnetwork(
            configStore = fakeStore(byJwt = "j", byClientJwt = "cj"),
            sdkBridge = bridge,
            authService = FakeAuth(),
            deviceIdentity = null,
            pluginScope = scope,
            statsPollIntervalMs = pollIntervalMs,
        )
        engine.start(baseConfig, Upstream.None)
        runCurrent()
        val firstSnapshot = engine.stats().first().activeConnections
        bridge.peerCountValue = 7
        advanceTimeBy(pollIntervalMs + 100)
        runCurrent()
        val secondSnapshot = engine.stats().first().activeConnections
        assertEquals(2, firstSnapshot)
        assertEquals(7, secondSnapshot)
        scope.cancel()
    }

    @Test
    fun `stop отменяет polling и сбрасывает stats`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val bridge = FakeBridge(initialPeerCount = 3)
        val engine = EngineUrnetwork(
            configStore = fakeStore(byJwt = "j", byClientJwt = "cj"),
            sdkBridge = bridge,
            authService = FakeAuth(),
            deviceIdentity = null,
            pluginScope = scope,
            statsPollIntervalMs = pollIntervalMs,
        )
        engine.start(baseConfig, Upstream.None)
        runCurrent()
        engine.stop()
        runCurrent()
        val afterStop = engine.stats().first()
        assertEquals(EngineStats(), afterStop, "после stop stats возвращается в default empty")
    }

    @Test
    fun `peer count = 0 — sentinel что bridge корректно проброшен`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val bridge = FakeBridge(initialPeerCount = 0)
        val engine = EngineUrnetwork(
            configStore = fakeStore(byJwt = "j", byClientJwt = "cj"),
            sdkBridge = bridge,
            authService = FakeAuth(),
            deviceIdentity = null,
            pluginScope = scope,
            statsPollIntervalMs = pollIntervalMs,
        )
        engine.start(baseConfig, Upstream.None)
        runCurrent()
        assertEquals(0, engine.stats().first().activeConnections)
        scope.cancel()
    }

    @Test
    fun `bridge_peerCount throw — polling продолжается с peers=0`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val bridge = FakeBridge(initialPeerCount = 0, throwOnPeerCount = true)
        val engine = EngineUrnetwork(
            configStore = fakeStore(byJwt = "j", byClientJwt = "cj"),
            sdkBridge = bridge,
            authService = FakeAuth(),
            deviceIdentity = null,
            pluginScope = scope,
            statsPollIntervalMs = pollIntervalMs,
        )
        engine.start(baseConfig, Upstream.None)
        runCurrent()
        assertEquals(0, engine.stats().first().activeConnections, "throw → peers=0, polling не падает")
        bridge.throwOnPeerCount = false
        bridge.peerCountValue = 4
        advanceTimeBy(pollIntervalMs + 100)
        runCurrent()
        assertEquals(4, engine.stats().first().activeConnections, "после восстановления peer count подхватывается")
        scope.cancel()
    }

    private class FakeBridge(
        initialPeerCount: Int = 0,
        var throwOnPeerCount: Boolean = false,
    ) : UrnetworkSdkBridge {
        var peerCountValue: Int = initialPeerCount
        var lastWallet: String = ""
        var startCalls: Int = 0
        var stopCalls: Int = 0
        var running: Boolean = false

        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byClientJwt: String,
        ): UrnetworkSdkBridge.StartResult {
            startCalls++
            running = true
            lastWallet = walletAddress
            return UrnetworkSdkBridge.StartResult.Success
        }

        override suspend fun stop() {
            stopCalls++
            running = false
        }

        override fun isRunning(): Boolean = running
        override suspend fun attachTun(tunFd: Int) = UrnetworkSdkBridge.AttachResult.Success
        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit
        override fun connectBestAvailable() = Unit
        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = null
        override fun openLocationsViewController(): com.bringyour.sdk.LocationsViewController? = null
        override fun setProvidePaused(paused: Boolean) = Unit
        override fun isProvidePaused(): Boolean = true
        override fun peerCount(): Int =
            if (throwOnPeerCount) throw IllegalStateException("bridge transient error") else peerCountValue
        override fun unpaidByteCount(): Long = 0L
        override fun fetchTransferStats() = Unit
        override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
    }

    private fun fakeStore(
        byJwt: String? = null,
        byClientJwt: String? = null,
    ): UrnetworkConfigStore =
        InMemoryUrnetworkConfigStore(UrnetworkConfig(byJwt = byJwt, byClientJwt = byClientJwt))

    private class FakeAuth : UrnetworkAuthService {
        override suspend fun acquireGuestJwt(): GuestJwtResult = GuestJwtResult.Success("g")
        override suspend fun acquireClientJwt(byJwt: String): ClientJwtResult = ClientJwtResult.Success("c")
    }
}
