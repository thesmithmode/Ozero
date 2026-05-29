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
    fun `awaitReady РІРѕР·РІСЂР°С‰Р°РµС‚ Ready РЅРµРјРµРґР»РµРЅРЅРѕ РєРѕРіРґР° peerCount СѓР¶Рµ Р±РѕР»СЊС€Рµ РЅСѓР»СЏ`() = runTest {
        val bridge = CountableBridge(fixedPeers = 3)
        val eng = engine(bridge, backgroundScope)
        eng.start(baseConfig, Upstream.None)

        val result = eng.awaitReady()

        assertEquals(EnginePlugin.ReadyResult.Ready, result, "peers>0 в†’ Ready Р±РµР· timeout")
        assertTrue(bridge.peerCountCalls.get() >= 1, "peerCount РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ РѕРїСЂРѕС€РµРЅ С…РѕС‚СЏ Р±С‹ СЂР°Р·")
    }

    @Test
    fun `awaitReady РІРѕР·РІСЂР°С‰Р°РµС‚ Ready РєРѕРіРґР° SDK СѓР¶Рµ CONNECTED РґР°Р¶Рµ Р±РµР· grid peers`() = runTest {
        val bridge = CountableBridge(fixedPeers = 0).also {
            it.connectionStatusProvider = { "CONNECTED" }
        }
        val eng = engine(bridge, backgroundScope)
        eng.start(baseConfig, Upstream.None)

        val result = eng.awaitReady()

        assertEquals(
            EnginePlugin.ReadyResult.Ready,
            result,
            "SDK CONNECTED в†’ Ready РґР°Р¶Рµ РµСЃР»Рё grid.windowCurrentSize РµС‰С‘ 0",
        )
        assertTrue(bridge.connectionStatusCalls.get() >= 1, "connectionStatus РґРѕР»Р¶РµРЅ Р±С‹С‚СЊ РѕРїСЂРѕС€РµРЅ")
    }

    @Test
    fun `awaitReady returns Ready after attach issued connect without waiting for peers`() = runTest {
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
        val eng = engine(bridge, backgroundScope)
        eng.start(baseConfig, Upstream.None)

        val result = eng.awaitReady()

        assertEquals(
            EnginePlugin.ReadyResult.Ready,
            result,
            "startup gate must not keep URnetwork in Connecting while runtime peer watchdog owns peer grace",
        )
    }

    @Test
    fun `awaitReady РІРѕР·РІСЂР°С‰Р°РµС‚ Ready РїРѕСЃР»Рµ РѕР¶РёРґР°РЅРёСЏ РїРѕРєР° peerCount РЅРµ СЃС‚Р°РЅРµС‚ РїРѕР»РѕР¶РёС‚РµР»СЊРЅС‹Рј`() = runTest {
        val bridge = CountableBridge(fixedPeers = 0)
        bridge.peerCountProvider = { if (bridge.peerCountCalls.get() >= 3) 1 else 0 }
        val eng = engine(bridge, backgroundScope, startupReadyPollMs = 50L)
        eng.start(baseConfig, Upstream.None)

        val result = eng.awaitReady()

        assertEquals(EnginePlugin.ReadyResult.Ready, result, "eventual peers>0 в†’ Ready")
        assertTrue(
            bridge.peerCountCalls.get() >= 3,
            "awaitReady РґРѕР»Р¶РµРЅ РѕРїСЂРѕСЃРёС‚СЊ РјРёРЅРёРјСѓРј 3 СЂР°Р·Р° РґРѕ СѓСЃРїРµС…Р°, calls=${bridge.peerCountCalls.get()}",
        )
    }

    @Test
    fun `awaitReady РІРѕР·РІСЂР°С‰Р°РµС‚ Timeout РєРѕРіРґР° peers РЅРёРєРѕРіРґР° РЅРµ РїРѕСЏРІР»СЏСЋС‚СЃСЏ вЂ” РЅРµ РјР°СЃРєРёСЂСѓРµС‚ РєР°Рє Ready`() = runTest {
        val bridge = CountableBridge(fixedPeers = 0)
        val eng = engine(bridge, backgroundScope, startupReadyTimeoutMs = 300L, startupReadyPollMs = 50L)
        eng.start(baseConfig, Upstream.None)

        val result = try {
            eng.awaitReady()
        } catch (_: Throwable) {
            fail("awaitReady РЅРµ РґРѕР»Р¶РµРЅ Р±СЂРѕСЃР°С‚СЊ РёСЃРєР»СЋС‡РµРЅРёРµ РїСЂРё С‚Р°Р№РјР°СѓС‚Рµ")
        }
        val timeout = assertIs<EnginePlugin.ReadyResult.Timeout>(
            result,
            "timeout РѕР±СЏР·Р°РЅ РІРµСЂРЅСѓС‚СЊ Timeout, РЅРµ Ready (root fix #59)",
        )
        assertTrue(
            timeout.reason.contains("URnetwork"),
            "reason РґРѕР»Р¶РµРЅ СЃРѕРґРµСЂР¶Р°С‚СЊ РёРјСЏ РґРІРёР¶РєР° РґР»СЏ РґРёР°РіРЅРѕСЃС‚РёРєРё, Р±С‹Р»Рѕ: ${timeout.reason}",
        )
        assertTrue(
            timeout.reason.contains("300"),
            "reason РґРѕР»Р¶РµРЅ СЃРѕРґРµСЂР¶Р°С‚СЊ timeout ms РґР»СЏ РґРёР°РіРЅРѕСЃС‚РёРєРё, Р±С‹Р»Рѕ: ${timeout.reason}",
        )
    }

    @Test
    fun `awaitReady РІРѕР·РІСЂР°С‰Р°РµС‚ Timeout РµСЃР»Рё peerCount РІСЃРµРіРґР° РєРёРґР°РµС‚ РёСЃРєР»СЋС‡РµРЅРёРµ`() = runTest {
        val bridge = CountableBridge(fixedPeers = 0).also {
            it.peerCountProvider = { throw IllegalStateException("bridge unavailable") }
        }
        val eng = engine(bridge, backgroundScope, startupReadyTimeoutMs = 300L, startupReadyPollMs = 50L)
        eng.start(baseConfig, Upstream.None)

        val result = try {
            eng.awaitReady()
        } catch (_: Throwable) {
            fail("awaitReady РЅРµ РґРѕР»Р¶РµРЅ РїСЂРѕР±СЂР°СЃС‹РІР°С‚СЊ РёСЃРєР»СЋС‡РµРЅРёСЏ РёР· peerCount")
        }
        assertIs<EnginePlugin.ReadyResult.Timeout>(
            result,
            "bridge throw в†’ 0 peers в†’ Timeout (root fix #59)",
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
            .find(source) ?: fail("STARTUP_READY_TIMEOUT_MS РЅРµ РЅР°Р№РґРµРЅ РІ EngineUrnetwork.kt")
        val raw = (match.groupValues[1] + match.groupValues[2])
        val ms = raw.toLong()
        assertTrue(
            ms in 1_000L..15_000L,
            "STARTUP_READY_TIMEOUT_MS=$ms must be a short attach/connect gate. " +
                "Runtime peer grace belongs to EngineWatchdogCoordinator.",
        )
    }

    @Test
    fun `sentinel awaitReady РїРёС€РµС‚ progress РІ boot log РїСЂРё РґРѕР»РіРѕРј peer discovery`() {
        val source = File("src/main/java/ru/ozero/engineurnetwork/EngineUrnetwork.kt").readText()
        val body = source.substringAfter("override suspend fun awaitReady(): EnginePlugin.ReadyResult")
            .substringBefore("override suspend fun attachTun")
        assertTrue(
            body.contains("STARTUP_PROGRESS_LOG_EVERY"),
            "awaitReady РѕР±СЏР·Р°РЅ Р»РѕРіРёСЂРѕРІР°С‚СЊ progress С‡РµСЂРµР· STARTUP_PROGRESS_LOG_EVERY РґР»СЏ РІРёРґРёРјРѕСЃС‚Рё РІ boot.log",
        )
        assertTrue(
            body.contains("PersistentLoggers.debug"),
            "progress log РѕР±СЏР·Р°РЅ РёРґС‚Рё С‡РµСЂРµР· PersistentLoggers.debug (boot.log persistent), РЅРµ info/warn",
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

