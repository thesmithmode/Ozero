package ru.ozero.enginewarp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.ExitNodeStrategy
import ru.ozero.enginescore.ProbeResult
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.Upstream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EngineWarpAdditionalCoverageTest {

    @Test
    fun `start rejects wrong config and upstream before resolving`() = runTest {
        val engine = newEngine()

        assertIs<EngineConfig.Warp>(engine.buildManualConfig(null))
        assertIs<EngineConfig.WarpProxy>(engine.buildProxyConfig(null))
        assertFailsWith<IllegalArgumentException> {
            engine.start(EngineConfig.ByeDpi(), Upstream.None)
        }
        assertFailsWith<IllegalArgumentException> {
            engine.start(EngineConfig.Warp, Upstream.Socks5("127.0.0.1", 1080))
        }
        assertFailsWith<IllegalArgumentException> {
            engine.start(EngineConfig.WarpProxy(1090), Upstream.Http("127.0.0.1", 8080))
        }
    }

    @Test
    fun `start returns failure when active slot missing and auto register fails`() = runTest {
        val engine = newEngine(
            store = FakeStore(emptyList()),
            auto = FakeAuto(Result.failure(IllegalStateException("down"))),
        )

        val result = engine.start(EngineConfig.Warp, Upstream.None)

        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun `auto register saves fresh slot and enables provider label exit node`() = runTest {
        val store = FakeStore(emptyList())
        val engine = newEngine(store = store, auto = FakeAuto(Result.success(RegisteredWarpConfig(config, rawIni()))))

        val result = engine.start(EngineConfig.Warp, Upstream.None)
        val strategy = engine.exitNodeStrategy(0)

        assertIs<StartResult.Success>(result)
        assertEquals(1, store.addCalls)
        assertIs<ExitNodeStrategy.ProviderLabel>(strategy)
    }

    @Test
    fun `second manual start reuses cached resolved config without reading store`() = runTest {
        val store = FakeStore(listOf(slot()))
        val engine = newEngine(store = store)

        assertIs<StartResult.Success>(engine.start(EngineConfig.Warp, Upstream.None))
        store.clear()
        assertIs<StartResult.Success>(engine.start(EngineConfig.Warp, Upstream.None))
    }

    @Test
    fun `manual start after proxy mode resolves fresh config instead of reusing proxy ini`() = runTest {
        val store = FakeStore(listOf(slot()))
        val engine = newEngine(bridge = FakeBridge(proxyResult = WarpSdkBridge.ProxyResult.Success), store = store)

        assertIs<StartResult.Success>(engine.start(EngineConfig.WarpProxy(19093), Upstream.None))
        store.clear()
        val result = engine.start(EngineConfig.Warp, Upstream.None)

        assertIs<StartResult.Failure>(result)
    }

    @Test
    fun `proxy start can reuse cached manual resolved config`() = runTest {
        val store = FakeStore(listOf(slot()))
        val bridge = FakeBridge(proxyResult = WarpSdkBridge.ProxyResult.Success)
        val engine = newEngine(bridge = bridge, store = store)

        assertIs<StartResult.Success>(engine.start(EngineConfig.Warp, Upstream.None))
        store.clear()
        val result = engine.start(EngineConfig.WarpProxy(19094), Upstream.None)

        assertIs<StartResult.Success>(result)
        assertEquals(1, bridge.proxyCalls)
    }

    @Test
    fun `auto register duplicate activates existing slot`() = runTest {
        val store = FakeStore(listOf(slot("existing")))
        store.throwDuplicateOnAdd = true
        store.clear()
        val engine = newEngine(store = store, auto = FakeAuto(Result.success(RegisteredWarpConfig(config, rawIni()))))

        engine.start(EngineConfig.Warp, Upstream.None)

        assertEquals("existing", store.activatedId)
    }

    @Test
    fun `tunSpec exposes route cidrs when config is split tunnel`() = runTest {
        val split = config.copy(
            allowedIps = listOf("10.0.0.0/8", "2001:db8::/32"),
            interfaceAddressV6 = "2001:db8::2/64",
            dnsServers = listOf("1.1.1.1", "2606:4700:4700::1111"),
        )
        val engine = newEngine(store = FakeStore(listOf(slot(config = split))), ipv6 = true)

        val spec = engine.tunSpec()

        assertEquals(listOf("10.0.0.0/8"), spec?.routeCidrsV4)
        assertEquals(listOf("2001:db8::/32"), spec?.routeCidrsV6)
        assertEquals(listOf("1.1.1.1", "2606:4700:4700::1111"), spec?.dnsServers)
    }

    @Test
    fun `tunSpec blackholes ipv6 and filters ipv6 dns when provider disables ipv6`() = runTest {
        val engine = newEngine(
            store = FakeStore(
                listOf(
                    slot(
                        config = config.copy(
                            allowedIps = listOf("0.0.0.0/0", "::/0"),
                            dnsServers = listOf("1.1.1.1", "2606:4700:4700::1111"),
                        ),
                    ),
                ),
            ),
            ipv6 = false,
        )

        val spec = engine.tunSpec()

        assertEquals(EngineWarp.WARP_IPV6_BLACKHOLE_ADDRESS, spec?.ipv6Address)
        assertEquals(listOf("1.1.1.1"), spec?.dnsServers)
        assertTrue(spec?.routeAllV4 == true)
        assertTrue(spec?.routeAllV6 == true)
    }

    @Test
    fun `raw ini parse failure still attaches generated raw endpoint`() = runTest {
        val bridge = FakeBridge()
        val raw = "[Interface]\nAddress = 172.16.0.2/32\n[Peer]\nEndpoint = old.example:2408\n"
        val engine = newEngine(
            bridge = bridge,
            store = FakeStore(listOf(slot(rawIni = raw, config = config.copy(peerEndpoint = "162.159.192.9:2408")))),
        )

        engine.start(EngineConfig.Warp, Upstream.None)
        engine.attachTun(44)

        assertTrue(bridge.lastIni.orEmpty().contains("Endpoint = 162.159.192.9:2408"))
    }

    @Test
    fun `recover second stale fails when saved fd or ini is unavailable`() = runTest {
        val engine = newEngine(reader = { _, _ -> WarpUapiState(null, 0L, 0L, 1) })

        assertIs<EnginePlugin.RecoverResult.Failed>(engine.recover())
        val second = engine.recover()

        val failure = assertIs<EnginePlugin.RecoverResult.Failed>(second)
        assertTrue(failure.reason.contains("reattach unavailable"))
    }

    @Test
    fun `stats poll handles reader throw without leaking active connection`() = runTest {
        val engine = newEngine(
            scope = backgroundScope,
            pollMs = 5L,
            reader = { _, _ -> error("uapi boom") },
        )

        engine.start(EngineConfig.Warp, Upstream.None)
        engine.attachTun(45)
        runCurrent()
        advanceTimeBy(20L)
        runCurrent()

        assertEquals(0, engine.stats().first().activeConnections)
    }

    @Test
    fun `stop clears proxy mode exit node`() = runTest {
        val bridge = FakeBridge(proxyResult = WarpSdkBridge.ProxyResult.Success)
        val engine = newEngine(bridge = bridge)

        engine.start(EngineConfig.WarpProxy(19091), Upstream.None)
        engine.stop()

        val strategy = engine.exitNodeStrategy(0)
        assertIs<ExitNodeStrategy.Unavailable>(strategy)
        assertEquals(1, bridge.stopProxyCalls)
    }

    @Test
    fun `proxy start failure does not activate proxy mode`() = runTest {
        val bridge = FakeBridge(proxyResult = WarpSdkBridge.ProxyResult.Failed("bind failed"))
        val engine = newEngine(bridge = bridge)

        val result = engine.start(EngineConfig.WarpProxy(19092), Upstream.None)

        val failure = assertIs<StartResult.Failure>(result)
        assertEquals("bind failed", failure.reason)
        assertIs<ExitNodeStrategy.Unavailable>(engine.exitNodeStrategy(0))
    }

    @Test
    fun `exit node strategy can use explicit socks port before start`() = runTest {
        val engine = newEngine()

        val strategy = engine.exitNodeStrategy(1080)

        val via = assertIs<ExitNodeStrategy.ViaSocks>(strategy)
        assertEquals("127.0.0.1", via.host)
        assertEquals(1080, via.port)
    }

    @Test
    fun `probe reports failure before proxy mode is active`() = runTest {
        val engine = newEngine()

        val result = engine.probe()

        assertIs<ProbeResult.Failure>(result)
    }

    @Test
    fun `awaitReady WARP timeout includes readable state diagnostics`() = runTest {
        val engine = newEngine(
            checker = { _, _ -> false },
            reader = { _, _ -> WarpUapiState(null, 11L, 22L, 2) },
        )
        engine.start(EngineConfig.Warp, Upstream.None)

        val result = engine.awaitReady()

        val timeout = assertIs<EnginePlugin.ReadyResult.Timeout>(result)
        assertTrue(timeout.reason.contains("rx=11"))
        assertTrue(timeout.reason.contains("lastHsAge=never"))
    }

    @Test
    fun `awaitReady WARP timeout includes socket listing when state is unreadable`() = runTest {
        val engine = newEngine(checker = { _, _ -> false }, reader = { _, _ -> null })
        engine.start(EngineConfig.Warp, Upstream.None)

        val result = engine.awaitReady()

        val timeout = assertIs<EnginePlugin.ReadyResult.Timeout>(result)
        assertTrue(timeout.reason.contains("uapi unreachable"))
        assertTrue(timeout.reason.contains("dirListing"))
    }

    @Test
    fun `awaitReady proxy mode times out when local socks never accepts`() = runTest {
        val engine = newEngine(bridge = FakeBridge(proxyResult = WarpSdkBridge.ProxyResult.Success))
        engine.start(EngineConfig.WarpProxy(19095), Upstream.None)

        val result = engine.awaitReady()

        val timeout = assertIs<EnginePlugin.ReadyResult.Timeout>(result)
        assertTrue(timeout.reason.contains("SOCKS timeout"))
    }

    @Test
    fun `tunSpec returns null when resolved config has blank ipv4 address`() = runTest {
        val engine = newEngine(store = FakeStore(listOf(slot(config = config.copy(interfaceAddressV4 = "/32")))))

        assertEquals(null, engine.tunSpec())
    }

    @Test
    fun `tunSpec uses default prefixes when addresses omit cidr suffix`() = runTest {
        val noCidr = config.copy(
            interfaceAddressV4 = "172.16.0.2",
            interfaceAddressV6 = "2606:4700::1",
        )
        val engine = newEngine(store = FakeStore(listOf(slot(config = noCidr))), ipv6 = true)

        val spec = engine.tunSpec()

        assertEquals(32, spec?.ipv4PrefixLength)
        assertEquals(128, spec?.ipv6PrefixLength)
    }

    @Test
    fun `tunSpec routeAllV6 is true for full IPv6 tunnel when ipv6 is enabled`() = runTest {
        val engine = newEngine(
            store = FakeStore(listOf(slot(config = config.copy(allowedIps = listOf("2001:db8::/32", "::/0"))))),
            ipv6 = true,
        )

        val spec = engine.tunSpec()

        assertTrue(spec?.routeAllV6 == true)
        assertEquals(emptyList(), spec?.routeCidrsV6)
    }

    @Test
    fun `exit node unavailable when resolved config has blank endpoint`() = runTest {
        val engine = newEngine(store = FakeStore(listOf(slot(config = config.copy(peerEndpoint = "")))))

        engine.start(EngineConfig.Warp, Upstream.None)
        val strategy = engine.exitNodeStrategy(0)

        assertIs<ExitNodeStrategy.Unavailable>(strategy)
    }

    @Test
    fun `attachTun failure returns reason and leaves bridge ini captured`() = runTest {
        val bridge = FakeBridge(attachResult = WarpSdkBridge.AttachResult.Failed("attach failed"))
        val engine = newEngine(bridge = bridge)

        engine.start(EngineConfig.Warp, Upstream.None)
        val result = engine.attachTun(46)

        val failure = assertIs<TunAttachResult.Failure>(result)
        assertEquals("attach failed", failure.reason)
        assertTrue(bridge.lastIni.orEmpty().contains("PrivateKey"))
    }

    @Test
    fun `recover second stale reattaches and succeeds when handshake returns`() = runTest {
        val bridge = FakeBridge()
        val engine = newEngine(
            bridge = bridge,
            checker = { _, _ -> true },
            reader = { _, _ -> WarpUapiState(999, 1L, 2L, 1) },
        )

        engine.start(EngineConfig.Warp, Upstream.None)
        engine.attachTun(47)
        assertIs<EnginePlugin.RecoverResult.Failed>(engine.recover())
        assertIs<EnginePlugin.RecoverResult.Success>(engine.recover())

        assertEquals(2, bridge.attachCalls)
        assertEquals(1, bridge.detachCalls)
    }

    @Test
    fun `recover second stale reports reattach failure`() = runTest {
        val bridge = FakeBridge(attachResult = WarpSdkBridge.AttachResult.Failed("wg down"))
        val engine = newEngine(
            bridge = bridge,
            reader = { _, _ -> WarpUapiState(999, 1L, 2L, 1) },
        )

        engine.start(EngineConfig.Warp, Upstream.None)
        assertIs<TunAttachResult.Failure>(engine.attachTun(48))
        assertIs<EnginePlugin.RecoverResult.Failed>(engine.recover())
        val second = assertIs<EnginePlugin.RecoverResult.Failed>(engine.recover())

        assertTrue(second.reason.contains("reattach failed"))
    }

    private fun newEngine(
        bridge: FakeBridge = FakeBridge(),
        store: FakeStore = FakeStore(listOf(slot())),
        auto: FakeAuto = FakeAuto(Result.failure(IllegalStateException("unused"))),
        scope: CoroutineScope? = null,
        pollMs: Long = 5_000L,
        ipv6: Boolean = false,
        reader: (String, String) -> WarpUapiState? = { _, _ -> null },
        checker: (String, String) -> Boolean = { _, _ -> true },
    ): EngineWarp = EngineWarp(
        autoConfig = auto,
        configStore = store,
        sdkBridge = bridge,
        uapiPathProvider = { "/tmp/uapi" },
        socketProtector = ru.ozero.enginescore.VpnSocketProtector { true },
        ipv6EnabledProvider = { ipv6 },
        handshakeChecker = checker,
        uapiStateReader = reader,
        warpReadyTimeoutMs = 20L,
        warpReadyPollMs = 5L,
        statsPollIntervalMs = pollMs,
        handshakeStaleThresholdSec = 180L,
        pluginScope = scope,
    )

    private class FakeAuto(private val result: Result<RegisteredWarpConfig>) : WarpAutoConfig {
        override suspend fun register(onProgress: ((String) -> Unit)?): Result<RegisteredWarpConfig> = result
    }

    private class FakeStore(initial: List<WarpConfigSlot>) : WarpConfigSlotStore {
        private val slotsState = MutableStateFlow(initial)
        var addCalls = 0
        var activatedId: String? = null
        var throwDuplicateOnAdd = false

        override fun slots(): Flow<List<WarpConfigSlot>> = slotsState
        override fun activeSlot(): Flow<WarpConfigSlot?> =
            MutableStateFlow(slotsState.value.firstOrNull { it.isActive })
        override fun activeConfig(): Flow<WarpConfig?> =
            MutableStateFlow(slotsState.value.firstOrNull { it.isActive }?.config)

        override suspend fun addSlot(
            name: String,
            config: WarpConfig,
            rawIni: String?,
            endpointList: List<String>,
        ): String {
            addCalls++
            if (throwDuplicateOnAdd) throw WarpConfigDuplicateException("existing", "Existing")
            val id = "slot-$addCalls"
            slotsState.value = slotsState.value + slot(id = id, config = config, rawIni = rawIni)
            return id
        }

        override suspend fun setActive(id: String) {
            activatedId = id
            slotsState.value = slotsState.value.map { it.copy(isActive = it.id == id) }
        }

        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun updateSlot(
            id: String,
            name: String,
            config: WarpConfig,
            rawIni: String?,
            endpointList: List<String>,
        ) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun clear() {
            slotsState.value = emptyList()
        }
        override suspend fun replaceAll(slots: List<WarpConfigSlot>) {
            slotsState.value = slots
        }
    }

    private class FakeBridge(
        private val attachResult: WarpSdkBridge.AttachResult = WarpSdkBridge.AttachResult.Success,
        private val proxyResult: WarpSdkBridge.ProxyResult = WarpSdkBridge.ProxyResult.Failed("proxy off"),
    ) : WarpSdkBridge {
        var stopProxyCalls = 0
        var attachCalls = 0
        var detachCalls = 0
        var proxyCalls = 0
        var lastIni: String? = null
        override suspend fun attachTun(
            tunnelName: String,
            tunFd: Int,
            iniConfig: String,
            uapiPath: String,
            protector: ru.ozero.enginescore.VpnSocketProtector,
        ): WarpSdkBridge.AttachResult {
            attachCalls++
            lastIni = iniConfig
            return attachResult
        }
        override suspend fun detachTun() {
            detachCalls++
        }
        override suspend fun startProxy(
            tunnelName: String,
            iniConfig: String,
            uapiPath: String,
            socksPort: Int,
            protector: ru.ozero.enginescore.VpnSocketProtector,
        ): WarpSdkBridge.ProxyResult {
            proxyCalls++
            return proxyResult
        }
        override suspend fun stopProxy() {
            stopProxyCalls++
        }
        override fun isRunning(): Boolean = false
        override fun reprotectSockets() = Unit
    }

    private companion object {
        val config = WarpConfig(
            privateKey = "private",
            publicKey = "public",
            peerPublicKey = "peer",
            peerEndpoint = "162.159.192.1:2408",
            interfaceAddressV4 = "172.16.0.2/32",
            interfaceAddressV6 = "2606:4700::1/128",
            allowedIps = listOf("0.0.0.0/0", "::/0"),
            dnsServers = listOf("1.1.1.1"),
            mtu = 1280,
            accountLicense = "license",
        )

        fun slot(
            id: String = "active",
            config: WarpConfig = this.config,
            rawIni: String? = null,
        ): WarpConfigSlot = WarpConfigSlot(
            id = id,
            name = "Active",
            config = config,
            isActive = true,
            rawIniOverride = rawIni,
        )

        fun rawIni(): String =
            """
            [Interface]
            PrivateKey = private
            Address = 172.16.0.2/32
            DNS = 1.1.1.1
            [Peer]
            PublicKey = peer
            AllowedIPs = 0.0.0.0/0
            Endpoint = 162.159.192.1:2408
            """.trimIndent()
    }
}
