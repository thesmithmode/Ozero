package ru.ozero.engineurnetwork

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.auth.ClientJwtResult
import ru.ozero.engineurnetwork.auth.GuestJwtResult
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.Upstream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class EngineUrnetworkAwaitReadyTest {

    private val baseConfig = EngineConfig.Urnetwork(jwtToken = "")

    private fun engine(
        bridge: UrnetworkSdkBridge,
        scope: CoroutineScope,
        startupReadyTimeoutMs: Long = 500L,
        startupReadyPollMs: Long = 50L,
    ) = EngineUrnetwork(
        configStore = minimalConfigStore,
        sdkBridge = bridge,
        jwtBootstrapper = RealUrnetworkJwtBootstrapper(minimalConfigStore, ImmediateAuthService, null),
        pluginScope = scope,
        statsPollIntervalMs = 10_000L,
        startupReadyTimeoutMs = startupReadyTimeoutMs,
        startupReadyPollMs = startupReadyPollMs,
    )

    @Test
    fun `awaitReady ready when peers positive`() = runTest {
        val bridge = CountableBridge(fixedPeers = 3)
        val eng = engine(bridge, backgroundScope)
        eng.start(baseConfig, Upstream.None)

        val result = eng.awaitReady()

        assertEquals(EnginePlugin.ReadyResult.Ready, result, "peers>0 -> Ready")
        assertTrue(bridge.peerCountCalls.get() >= 1, "peerCount should be queried")
    }

    @Test
    fun `awaitReady ready when SDK connected without peers`() = runTest {
        val bridge = CountableBridge(fixedPeers = 0).also {
            it.connectionStatusProvider = { "CONNECTED" }
        }
        val eng = engine(bridge, backgroundScope)
        eng.start(baseConfig, Upstream.None)

        val result = eng.awaitReady()

        assertEquals(
            EnginePlugin.ReadyResult.Ready,
            result,
            "SDK CONNECTED -> Ready without peers",
        )
        assertTrue(bridge.connectionStatusCalls.get() >= 1, "connectionStatus should be queried")
    }

    @Test
    fun `awaitReady times out after attach issued connect without usable runtime state`() = runTest {
        val bridge = CountableBridge(fixedPeers = 0).also {
            it.runtimeSnapshotProvider = {
                UrnetworkSdkBridge.RuntimeSnapshot(
                    connectionStatus = "CONNECTING",
                    peers = 0,
                    providerStateAdded = 0L,
                    tunnelStarted = true,
                    connectIssued = true,
                )
            }
        }
        val eng = engine(bridge, backgroundScope, startupReadyTimeoutMs = 300L, startupReadyPollMs = 50L)
        eng.start(baseConfig, Upstream.None)

        val result = eng.awaitReady()

        assertIs<EnginePlugin.ReadyResult.Timeout>(
            result,
            "connectIssued without provider state, peers, or CONNECTED status must not be Ready",
        )
    }

    @Test
    fun `awaitReady ready when peers appear later`() = runTest {
        val bridge = CountableBridge(fixedPeers = 0)
        bridge.peerCountProvider = { if (bridge.peerCountCalls.get() >= 3) 1 else 0 }
        val eng = engine(bridge, backgroundScope, startupReadyPollMs = 50L)
        eng.start(baseConfig, Upstream.None)

        val result = eng.awaitReady()

        assertEquals(EnginePlugin.ReadyResult.Ready, result, "eventual peers>0 -> Ready")
        assertTrue(
            bridge.peerCountCalls.get() >= 3,
            "expected >=3 peerCount calls, calls=${bridge.peerCountCalls.get()}",
        )
    }

    @Test
    fun `awaitReady timeout when peers never appear`() = runTest {
        val bridge = CountableBridge(fixedPeers = 0)
        val eng = engine(bridge, backgroundScope, startupReadyTimeoutMs = 300L, startupReadyPollMs = 50L)
        eng.start(baseConfig, Upstream.None)

        val result = try {
            eng.awaitReady()
        } catch (_: Throwable) {
            fail("awaitReady must not throw on timeout")
        }
        val timeout = assertIs<EnginePlugin.ReadyResult.Timeout>(
            result,
            "timeout must return Timeout",
        )
        assertTrue(
            timeout.reason.contains("URnetwork"),
            "reason must include engine name, was: ${timeout.reason}",
        )
        assertTrue(
            timeout.reason.contains("300"),
            "reason РґРѕР»Р¶РµРЅ СЃРѕРґРµСЂР¶Р°С‚СЊ timeout ms РґР»СЏ РґРёР°РіРЅРѕСЃС‚РёРєРё, Р±С‹Р»Рѕ: ${timeout.reason}",
        )
    }

    @Test
    fun `awaitReady timeout when peerCount always throws`() = runTest {
        val bridge = CountableBridge(fixedPeers = 0).also {
            it.peerCountProvider = { throw IllegalStateException("bridge unavailable") }
        }
        val eng = engine(bridge, backgroundScope, startupReadyTimeoutMs = 300L, startupReadyPollMs = 50L)
        eng.start(baseConfig, Upstream.None)

        val result = try {
            eng.awaitReady()
        } catch (_: Throwable) {
            fail("awaitReady must not rethrow peerCount errors")
        }
        assertIs<EnginePlugin.ReadyResult.Timeout>(
            result,
            "bridge throw -> Timeout",
        )
    }

    @Test
    fun `awaitReady returns Ready for lowercase connected status`() = runTest {
        val bridge = CountableBridge(fixedPeers = 0).also {
            it.connectionStatusProvider = { "connected" }
        }
        val eng = engine(bridge, backgroundScope, startupReadyTimeoutMs = 300L, startupReadyPollMs = 50L)
        eng.start(baseConfig, Upstream.None)

        val result = eng.awaitReady()

        assertEquals(EnginePlugin.ReadyResult.Ready, result, "status=connected (lowercase) must still become Ready")
        assertTrue(bridge.connectionStatusCalls.get() >= 1, "connectionStatus should be queried")
    }

    @Test
    fun `awaitReady returns Ready when peers positive even if connectionStatus throws`() = runTest {
        val bridge = CountableBridge(fixedPeers = 2).also {
            it.connectionStatusProvider = { throw IllegalStateException("status channel unavailable") }
        }
        val eng = engine(bridge, backgroundScope, startupReadyTimeoutMs = 300L, startupReadyPollMs = 50L)
        eng.start(baseConfig, Upstream.None)

        val result = eng.awaitReady()

        assertEquals(EnginePlugin.ReadyResult.Ready, result, "peers>0 must drive Ready even with status read failure")
        assertTrue(bridge.peerCountCalls.get() >= 1, "peerCount should be queried")
    }

    @Test
    fun `sentinel STARTUP_READY_TIMEOUT_MS stays bounded and does not hold runtime peer grace`() {
        val source = File("src/main/java/ru/ozero/engineurnetwork/EngineUrnetwork.kt").readText()
        val match = Regex("STARTUP_READY_TIMEOUT_MS\\s*=\\s*(\\d+)_?(\\d*)L")
            .find(source) ?: fail("STARTUP_READY_TIMEOUT_MS not found in EngineUrnetwork.kt")
        val raw = (match.groupValues[1] + match.groupValues[2])
        val ms = raw.toLong()
        assertTrue(
            ms in 1_000L..15_000L,
            "STARTUP_READY_TIMEOUT_MS=$ms must be a short attach/connect gate. " +
                "Runtime peer grace belongs to EngineWatchdogCoordinator.",
        )
    }

    @Test
    fun `sentinel awaitReady writes progress to boot log`() {
        val source = File("src/main/java/ru/ozero/engineurnetwork/EngineUrnetwork.kt").readText()
        val body = source.substringAfter("override suspend fun awaitReady(): EnginePlugin.ReadyResult")
            .substringBefore("override suspend fun attachTun")
        assertTrue(
            body.contains("STARTUP_PROGRESS_LOG_EVERY"),
            "awaitReady must log progress through STARTUP_PROGRESS_LOG_EVERY for boot.log visibility",
        )
        assertTrue(
            body.contains("PersistentLoggers.debug"),
            "progress log must use PersistentLoggers.debug, not info or warn",
        )
    }

    private val minimalConfigStore = InMemoryUrnetworkConfigStore(
        UrnetworkConfig(byJwt = "j", byClientJwt = "cj"),
    )

    private object ImmediateAuthService : UrnetworkAuthService {
        override suspend fun acquireGuestJwt() = GuestJwtResult.Success("j")
        override suspend fun acquireClientJwt(byJwt: String) = ClientJwtResult.Success("cj")
    }

    private class CountableBridge(
        private val fixedPeers: Int = 0,
    ) : UrnetworkSdkBridge {
        var peerCountProvider: (() -> Int)? = null
        var connectionStatusProvider: (() -> String?)? = null
        var runtimeSnapshotProvider: (() -> UrnetworkSdkBridge.RuntimeSnapshot)? = null
        val peerCountCalls = AtomicInteger(0)
        val connectionStatusCalls = AtomicInteger(0)

        override fun connectionStatus(): String? {
            connectionStatusCalls.incrementAndGet()
            return connectionStatusProvider?.invoke()
        }

        override fun peerCount(): Int {
            peerCountCalls.incrementAndGet()
            return peerCountProvider?.invoke() ?: fixedPeers
        }

        override fun runtimeSnapshot(): UrnetworkSdkBridge.RuntimeSnapshot =
            runtimeSnapshotProvider?.invoke() ?: UrnetworkSdkBridge.RuntimeSnapshot(
                connectionStatus = connectionStatus(),
                peers = peerCount(),
            )

        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byClientJwt: String,
        ) = UrnetworkSdkBridge.StartResult.Success

        override suspend fun stop() = Unit
        override fun isRunning() = true
        override suspend fun attachTun(tunFd: Int) = UrnetworkSdkBridge.AttachResult.Success
        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit
        override fun connectBestAvailable() = Unit
        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = null
        override fun openLocationsViewController(): com.bringyour.sdk.LocationsViewController? = null
        override fun setProvidePaused(paused: Boolean) = Unit
        override fun isProvidePaused() = false
        override fun unpaidByteCount() = 0L
        override fun fetchTransferStats() = Unit
        override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
    }
}
