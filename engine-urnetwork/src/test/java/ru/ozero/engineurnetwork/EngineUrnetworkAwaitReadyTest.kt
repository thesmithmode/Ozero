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
        peerReadyTimeoutMs: Long = 500L,
        peerReadyPollMs: Long = 50L,
    ) = EngineUrnetwork(
        configStore = minimalConfigStore,
        sdkBridge = bridge,
        jwtBootstrapper = RealUrnetworkJwtBootstrapper(minimalConfigStore, ImmediateAuthService, null),
        pluginScope = scope,
        statsPollIntervalMs = 10_000L,
        peerReadyTimeoutMs = peerReadyTimeoutMs,
        peerReadyPollMs = peerReadyPollMs,
    )

    @Test
    fun `awaitReady возвращает Ready немедленно когда peerCount уже больше нуля`() = runTest {
        val bridge = CountableBridge(fixedPeers = 3)
        val eng = engine(bridge, backgroundScope)
        eng.start(baseConfig, Upstream.None)

        val result = eng.awaitReady()

        assertEquals(EnginePlugin.ReadyResult.Ready, result, "peers>0 → Ready без timeout")
        assertTrue(bridge.peerCountCalls.get() >= 1, "peerCount должен быть опрошен хотя бы раз")
    }

    @Test
    fun `awaitReady возвращает Ready после ожидания пока peerCount не станет положительным`() = runTest {
        val bridge = CountableBridge(fixedPeers = 0)
        bridge.peerCountProvider = { if (bridge.peerCountCalls.get() >= 3) 1 else 0 }
        val eng = engine(bridge, backgroundScope, peerReadyPollMs = 50L)
        eng.start(baseConfig, Upstream.None)

        val result = eng.awaitReady()

        assertEquals(EnginePlugin.ReadyResult.Ready, result, "eventual peers>0 → Ready")
        assertTrue(
            bridge.peerCountCalls.get() >= 3,
            "awaitReady должен опросить минимум 3 раза до успеха, calls=${bridge.peerCountCalls.get()}",
        )
    }

    @Test
    fun `awaitReady возвращает Timeout когда peers никогда не появляются — не маскирует как Ready`() = runTest {
        val bridge = CountableBridge(fixedPeers = 0)
        val eng = engine(bridge, backgroundScope, peerReadyTimeoutMs = 300L, peerReadyPollMs = 50L)
        eng.start(baseConfig, Upstream.None)

        val result = try {
            eng.awaitReady()
        } catch (_: Throwable) {
            fail("awaitReady не должен бросать исключение при таймауте")
        }
        val timeout = assertIs<EnginePlugin.ReadyResult.Timeout>(
            result,
            "timeout обязан вернуть Timeout, не Ready (root fix #59)",
        )
        assertTrue(
            timeout.reason.contains("URnetwork"),
            "reason должен содержать имя движка для диагностики, было: ${timeout.reason}",
        )
        assertTrue(
            timeout.reason.contains("300"),
            "reason должен содержать timeout ms для диагностики, было: ${timeout.reason}",
        )
    }

    @Test
    fun `awaitReady возвращает Timeout если peerCount всегда кидает исключение`() = runTest {
        val bridge = CountableBridge(fixedPeers = 0).also {
            it.peerCountProvider = { throw IllegalStateException("bridge unavailable") }
        }
        val eng = engine(bridge, backgroundScope, peerReadyTimeoutMs = 300L, peerReadyPollMs = 50L)
        eng.start(baseConfig, Upstream.None)

        val result = try {
            eng.awaitReady()
        } catch (_: Throwable) {
            fail("awaitReady не должен пробрасывать исключения из peerCount")
        }
        assertIs<EnginePlugin.ReadyResult.Timeout>(
            result,
            "bridge throw → 0 peers → Timeout (root fix #59)",
        )
    }

    @Test
    fun `sentinel PEER_READY_TIMEOUT_MS не ниже 30s — initial P2P discovery медленно на плохих сетях`() {
        val source = File("src/main/java/ru/ozero/engineurnetwork/EngineUrnetwork.kt").readText()
        val match = Regex("PEER_READY_TIMEOUT_MS\\s*=\\s*(\\d+)_?(\\d*)L")
            .find(source) ?: fail("PEER_READY_TIMEOUT_MS не найден в EngineUrnetwork.kt")
        val raw = (match.groupValues[1] + match.groupValues[2])
        val ms = raw.toLong()
        assertTrue(
            ms >= 30_000L,
            "PEER_READY_TIMEOUT_MS=$ms ниже 30000 — initial P2P peer discovery " +
                "требует запаса. Регрессия 15s — было слишком жёстко.",
        )
    }

    @Test
    fun `sentinel awaitReady пишет progress в boot log при долгом peer discovery`() {
        val source = File("src/main/java/ru/ozero/engineurnetwork/EngineUrnetwork.kt").readText()
        val body = source.substringAfter("override suspend fun awaitReady(): EnginePlugin.ReadyResult")
            .substringBefore("override suspend fun attachTun")
        assertTrue(
            body.contains("PEER_PROGRESS_LOG_EVERY"),
            "awaitReady обязан логировать progress через PEER_PROGRESS_LOG_EVERY для видимости в boot.log",
        )
        assertTrue(
            body.contains("PersistentLoggers.info") || body.contains("PersistentLoggers.debug"),
            "progress log обязан идти через PersistentLoggers (boot.log persistent), не Log.i",
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
        val peerCountCalls = AtomicInteger(0)

        override fun peerCount(): Int {
            peerCountCalls.incrementAndGet()
            return peerCountProvider?.invoke() ?: fixedPeers
        }

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
