package ru.ozero.enginewarp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.Upstream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EngineWarpStartAttachTest {

    private val sampleConfig = WarpConfig(
        privateKey = "p",
        publicKey = "P",
        peerPublicKey = "PP",
        peerEndpoint = "162.159.192.1:2408",
        interfaceAddressV4 = "172.16.0.2/32",
        interfaceAddressV6 = "2606:4700::1/128",
        accountLicense = "L",
    )

    @Test
    fun `second stale recover reattaches saved tun and succeeds after handshake`() = runTest {
        val bridge = FakeBridge()
        val reader = FixedReader(
            WarpUapiState(handshakeAgeSeconds = 999L, rxBytes = 0L, txBytes = 0L, peersSeen = 1),
        )
        val engine = newEngine(bridge = bridge, reader = reader, scope = backgroundScope)
        engine.start(EngineConfig.Warp, Upstream.None)
        engine.attachTun(tunFd = 11)

        assertIs<EnginePlugin.RecoverResult.Failed>(engine.recover())
        val result = engine.recover()

        assertEquals(EnginePlugin.RecoverResult.Success, result)
        assertEquals(2, bridge.attachCalls)
        assertEquals(1, bridge.detachCalls)
    }

    @Test
    fun `second stale recover reports failed when reattach fails`() = runTest {
        val bridge = FakeBridge(attachResult = WarpSdkBridge.AttachResult.Failed("attach down"))
        val reader = FixedReader(
            WarpUapiState(handshakeAgeSeconds = 999L, rxBytes = 0L, txBytes = 0L, peersSeen = 1),
        )
        val engine = newEngine(bridge = bridge, reader = reader, scope = backgroundScope)
        engine.start(EngineConfig.Warp, Upstream.None)
        engine.attachTun(tunFd = 11)

        assertIs<EnginePlugin.RecoverResult.Failed>(engine.recover())
        val result = engine.recover()

        val failed = assertIs<EnginePlugin.RecoverResult.Failed>(result)
        assertTrue(failed.reason.contains("reattach failed"))
    }

    @Test
    fun `proxy start success stores socks port and stop calls proxy bridge`() = runTest {
        val bridge = FakeBridge(proxyResult = WarpSdkBridge.ProxyResult.Success)
        val engine = newEngine(bridge = bridge, reader = FixedReader(null), scope = backgroundScope)

        val result = engine.start(EngineConfig.WarpProxy(socksPort = SOCKS_PORT), Upstream.None)
        val strategy = engine.exitNodeStrategy(0)
        engine.stop()

        assertIs<StartResult.Success>(result)
        assertEquals(SOCKS_PORT, result.socksPort)
        assertEquals(1, bridge.proxyCalls)
        assertEquals(1, bridge.stopProxyCalls)
        assertEquals("127.0.0.1", (strategy as ExitNodeStrategy.ViaSocks).host)
        assertEquals(SOCKS_PORT, strategy.port)
    }

    @Test
    fun `proxy start failure returns start failure and does not mark running`() = runTest {
        val bridge = FakeBridge(proxyResult = WarpSdkBridge.ProxyResult.Failed("proxy unavailable"))
        val engine = newEngine(bridge = bridge, reader = FixedReader(null), scope = backgroundScope)

        val result = engine.start(EngineConfig.WarpProxy(socksPort = SOCKS_PORT + 1), Upstream.None)

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("proxy unavailable"))
        assertEquals(1, bridge.proxyCalls)
    }

    @Test
    fun `attachTun failure closes branch as TunAttachResult failure`() = runTest {
        val engine = newEngine(
            bridge = FakeBridge(attachResult = WarpSdkBridge.AttachResult.Failed("attach failed")),
            reader = FixedReader(null),
            scope = backgroundScope,
        )
        engine.start(EngineConfig.Warp, Upstream.None)

        val result = engine.attachTun(tunFd = 12)

        val failure = assertIs<TunAttachResult.Failure>(result)
        assertTrue(failure.reason.contains("attach failed"))
    }

    @Test
    fun `tunSpec uses configured ipv6 when provider allows it`() = runTest {
        val engine = newEngine(
            bridge = FakeBridge(),
            reader = FixedReader(null),
            scope = backgroundScope,
            ipv6Enabled = true,
        )

        val spec = engine.tunSpec()

        assertEquals("2606:4700::1", spec?.ipv6Address)
        assertEquals(128, spec?.ipv6PrefixLength)
        assertTrue(spec?.allowFamilyV6 == true)
    }

    @Test
    fun `tunSpec returns null when resolved config has no IPv4 address`() = runTest {
        val noAddress = sampleConfig.copy(interfaceAddressV4 = "")
        val engine = newEngine(
            bridge = FakeBridge(),
            reader = FixedReader(null),
            scope = backgroundScope,
            activeConfig = noAddress,
        )

        val spec = engine.tunSpec()

        assertEquals(null, spec)
    }

    @Test
    fun `system resolver rewrites hostname endpoints before attach`() = runTest {
        val bridge = FakeBridge()
        val hostConfig = sampleConfig.copy(
            peerEndpoint = "localhost:2408",
            doHProvider = DoHProvider.SYSTEM,
        )
        val engine = newEngine(
            bridge = bridge,
            reader = FixedReader(null),
            scope = backgroundScope,
            activeConfig = hostConfig,
        )

        engine.start(EngineConfig.Warp, Upstream.None)
        engine.attachTun(tunFd = 12)

        val ini = bridge.lastIni ?: error("ini missing")
        assertTrue(ini.contains("Endpoint = 127.0.0.1:2408") || ini.contains("Endpoint = 0:0:0:0:0:0:0:1:2408"))
    }

    @Test
    fun `proxy start first then normal start rebuilds ini without socks block`() = runTest {
        val bridge = FakeBridge(proxyResult = WarpSdkBridge.ProxyResult.Success)
        val engine = newEngine(
            bridge = bridge,
            reader = FixedReader(null),
            scope = backgroundScope,
        )

        val proxy = engine.start(EngineConfig.WarpProxy(socksPort = SOCKS_PORT), Upstream.None)
        val regular = engine.start(EngineConfig.Warp, Upstream.None)
        val attach = engine.attachTun(tunFd = 12)

        assertIs<StartResult.Success>(proxy)
        assertIs<StartResult.Success>(regular)
        assertIs<TunAttachResult.Success>(attach)
        val ini = bridge.lastIni ?: error("ini missing")
        assertFalse(
            ini.contains("[Socks5]"),
            "cached proxy ini must be bypassed after activeSocksPort cleanup",
        )
        assertTrue(
            ini.contains("Endpoint = ${sampleConfig.peerEndpoint}"),
            "endpoint should come from active slot config",
        )
    }

    @Test
    fun `attachTun before start returns TunAttachResult failure`() = runTest {
        val engine = newEngine(bridge = FakeBridge(), reader = FixedReader(null), scope = backgroundScope)
        val result = engine.attachTun(tunFd = 17)
        val failure = assertIs<TunAttachResult.Failure>(result)
        assertEquals("attachTun before start - no ini config", failure.reason)
    }

    private fun interface WarpUapiStateReader {
        operator fun invoke(uapiPath: String, tunnelName: String): WarpUapiState?
    }

    private class FixedReader(private val state: WarpUapiState?) : WarpUapiStateReader {
        override fun invoke(uapiPath: String, tunnelName: String): WarpUapiState? = state
    }

    private fun newEngine(
        bridge: FakeBridge,
        reader: WarpUapiStateReader,
        scope: CoroutineScope,
        ipv6Enabled: Boolean = false,
        activeConfig: WarpConfig = sampleConfig,
    ): EngineWarp = EngineWarp(
        autoConfig = FakeAuto(),
        configStore = FakeStore(activeConfig),
        sdkBridge = bridge,
        uapiPathProvider = { "/tmp/uapi" },
        socketProtector = ru.ozero.enginescore.VpnSocketProtector { true },
        ipv6EnabledProvider = { ipv6Enabled },
        handshakeChecker = { _, _ -> true },
        uapiStateReader = { path, name -> reader(path, name) },
        warpReadyTimeoutMs = 100L,
        warpReadyPollMs = 10L,
        statsPollIntervalMs = 5_000L,
        handshakeStaleThresholdSec = 180L,
        pluginScope = scope,
    )

    private class FakeAuto : WarpAutoConfig {
        override suspend fun register(onProgress: ((String) -> Unit)?): Result<RegisteredWarpConfig> =
            error("not used")
    }

    private class FakeStore(private val active: WarpConfig) : WarpConfigSlotStore {
        private val slots = MutableStateFlow(
            listOf(
                WarpConfigSlot(
                    id = "fixed",
                    name = "Fixed",
                    config = active,
                    isActive = true,
                    rawIniOverride = null,
                ),
            ),
        )
        override fun slots(): Flow<List<WarpConfigSlot>> = slots
        override fun activeSlot(): Flow<WarpConfigSlot?> =
            MutableStateFlow(slots.value.firstOrNull { it.isActive })
        override fun activeConfig(): Flow<WarpConfig?> = MutableStateFlow(active)
        override suspend fun addSlot(
            name: String,
            config: WarpConfig,
            rawIni: String?,
            endpointList: List<String>,
        ): String = "id"
        override suspend fun setActive(id: String) = Unit
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun updateSlot(
            id: String,
            name: String,
            config: WarpConfig,
            rawIni: String?,
            endpointList: List<String>,
        ) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun clear() = Unit
        override suspend fun replaceAll(slots: List<WarpConfigSlot>) = Unit
    }

    private class FakeBridge(
        private val attachResult: WarpSdkBridge.AttachResult = WarpSdkBridge.AttachResult.Success,
        private val proxyResult: WarpSdkBridge.ProxyResult = WarpSdkBridge.ProxyResult.Failed("not supported"),
    ) : WarpSdkBridge {
        var attachCalls = 0
        var detachCalls = 0
        var proxyCalls = 0
        var stopProxyCalls = 0
        var lastIni: String? = null
        private var running = false

        override suspend fun attachTun(
            tunnelName: String,
            tunFd: Int,
            iniConfig: String,
            uapiPath: String,
            protector: ru.ozero.enginescore.VpnSocketProtector,
        ): WarpSdkBridge.AttachResult {
            attachCalls++
            lastIni = iniConfig
            running = attachResult is WarpSdkBridge.AttachResult.Success
            return attachResult
        }

        override suspend fun detachTun() {
            detachCalls++
            running = false
        }

        override suspend fun startProxy(
            tunnelName: String,
            iniConfig: String,
            uapiPath: String,
            socksPort: Int,
            protector: ru.ozero.enginescore.VpnSocketProtector,
        ): WarpSdkBridge.ProxyResult {
            proxyCalls++
            running = proxyResult is WarpSdkBridge.ProxyResult.Success
            return proxyResult
        }

        override suspend fun stopProxy() {
            stopProxyCalls++
            running = false
        }

        override fun isRunning(): Boolean = running
        override fun reprotectSockets() {}
    }

    private companion object {
        const val SOCKS_PORT = 19090
    }
}
