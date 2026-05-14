package ru.ozero.enginewarp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.TunAttachResult
import ru.ozero.enginescore.TunFdAcceptor
import ru.ozero.enginescore.Upstream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class EngineWarpContractTest {

    private val sampleConfig = WarpConfig(
        privateKey = "p",
        publicKey = "P",
        peerPublicKey = "PP",
        peerEndpoint = "162.159.192.1:2408",
        interfaceAddressV4 = "172.16.0.2/32",
        interfaceAddressV6 = "2606:4700::1/128",
        accountLicense = "L",
    )

    private fun engine(
        activeConfig: WarpConfig? = null,
        activeRawIni: String? = null,
        autoConfigResult: Result<RegisteredWarpConfig> = Result.success(
            RegisteredWarpConfig(sampleConfig, "[Interface]\n[Peer]\n"),
        ),
        bridge: FakeWarpSdkBridge = FakeWarpSdkBridge(),
        uapiPath: String = "/data/data/ru.ozero.app",
        ipv6Enabled: Boolean = true,
        handshakeChecker: (String, String) -> Boolean = { _, _ -> true },
        warpReadyTimeoutMs: Long = 500L,
        warpReadyPollMs: Long = 50L,
    ): Triple<EngineWarp, FakeWarpAutoConfig, FakeWarpConfigSlotStore> {
        val store = FakeWarpConfigSlotStore(activeConfig = activeConfig, activeRawIni = activeRawIni)
        val auto = FakeWarpAutoConfig(autoConfigResult)
        val e = EngineWarp(
            autoConfig = auto,
            configStore = store,
            sdkBridge = bridge,
            uapiPathProvider = { uapiPath },
            socketProtector = ru.ozero.enginescore.VpnSocketProtector { true },
            ipv6EnabledProvider = { ipv6Enabled },
            handshakeChecker = handshakeChecker,
            warpReadyTimeoutMs = warpReadyTimeoutMs,
            warpReadyPollMs = warpReadyPollMs,
        )
        return Triple(e, auto, store)
    }

    @Test
    fun `id равен WARP`() {
        val (e, _, _) = engine()
        assertEquals(EngineId.WARP, e.id)
    }

    @Test
    fun `EngineWarp implements TunFdAcceptor`() {
        val (e, _, _) = engine()
        assertTrue(e is TunFdAcceptor)
    }

    @Test
    fun `start без active config регистрирует и кеширует ini`() = runTest {
        val (e, auto, store) = engine(activeConfig = null)
        val r = e.start(EngineConfig.Warp, Upstream.None)
        assertIs<StartResult.Success>(r)
        assertEquals(1, auto.registerCalls)
        assertEquals(sampleConfig, store.lastAdded?.config)
    }

    @Test
    fun `start с active config пропускает register`() = runTest {
        val (e, auto, _) = engine(activeConfig = sampleConfig)
        val r = e.start(EngineConfig.Warp, Upstream.None)
        assertIs<StartResult.Success>(r)
        assertEquals(0, auto.registerCalls)
    }

    @Test
    fun `register failure → StartResult Failure`() = runTest {
        val (e, _, _) = engine(
            activeConfig = null,
            autoConfigResult = Result.failure<RegisteredWarpConfig>(java.io.IOException("net down")),
        )
        val r = e.start(EngineConfig.Warp, Upstream.None)
        assertIs<StartResult.Failure>(r)
    }

    @Test
    fun `start не зовёт bridge attachTun — это делает VpnService`() = runTest {
        val bridge = FakeWarpSdkBridge()
        val (e, _, _) = engine(activeConfig = sampleConfig, bridge = bridge)
        e.start(EngineConfig.Warp, Upstream.None)
        assertEquals(0, bridge.attachCalls, "start не должен звать attachTun — fd ещё не известен")
    }

    @Test
    fun `attachTun без start → Failure`() = runTest {
        val (e, _, _) = engine(activeConfig = sampleConfig)
        val r = e.attachTun(tunFd = 42)
        assertIs<TunAttachResult.Failure>(r)
    }

    @Test
    fun `attachTun после start вызывает bridge attachTun с ini и uapiPath`() = runTest {
        val bridge = FakeWarpSdkBridge()
        val (e, _, _) = engine(
            activeConfig = sampleConfig,
            bridge = bridge,
            uapiPath = "/data/data/test",
        )
        e.start(EngineConfig.Warp, Upstream.None)
        val r = e.attachTun(tunFd = 7)
        assertIs<TunAttachResult.Success>(r)
        assertEquals(1, bridge.attachCalls)
        assertEquals(7, bridge.lastFd)
        assertEquals("/data/data/test", bridge.lastUapi)
        assertNotNull(bridge.lastIni)
        assertTrue(bridge.lastIni!!.contains("PrivateKey = ${sampleConfig.privateKey}"))
        assertTrue(bridge.lastIni!!.contains("Endpoint = ${sampleConfig.peerEndpoint}"))
    }

    @Test
    fun `attachTun проксирует bridge Failure`() = runTest {
        val bridge = FakeWarpSdkBridge(
            attachResult = WarpSdkBridge.AttachResult.Failed("awgTurnOn handle=-1"),
        )
        val (e, _, _) = engine(activeConfig = sampleConfig, bridge = bridge)
        e.start(EngineConfig.Warp, Upstream.None)
        val r = e.attachTun(tunFd = 7)
        val f = assertIs<TunAttachResult.Failure>(r)
        assertTrue(f.reason.contains("awgTurnOn"))
    }

    @Test
    fun `stop вызывает bridge detachTun`() = runTest {
        val bridge = FakeWarpSdkBridge()
        val (e, _, _) = engine(activeConfig = sampleConfig, bridge = bridge)
        e.start(EngineConfig.Warp, Upstream.None)
        e.stop()
        assertEquals(1, bridge.detachCalls)
    }

    @Test
    fun `tunSpec без active config регистрирует через autoConfig`() = runTest {
        val (e, auto, _) = engine(activeConfig = null)
        val spec = e.tunSpec()
        assertNotNull(spec)
        assertEquals(1, auto.registerCalls)
    }

    @Test
    fun `tunSpec возвращает корректные параметры из WarpConfig`() = runTest {
        val (e, _, _) = engine(activeConfig = sampleConfig)
        val spec = e.tunSpec()!!
        assertEquals("WARP", spec.sessionName)
        assertEquals(sampleConfig.mtu, spec.mtu)
        assertEquals("172.16.0.2", spec.ipv4Address)
        assertEquals(32, spec.ipv4PrefixLength)
        assertEquals("2606:4700::1", spec.ipv6Address)
        assertEquals(128, spec.ipv6PrefixLength)
        assertEquals(sampleConfig.dnsServers, spec.dnsServers)
        assertTrue(spec.allowFamilyV4)
        assertTrue(spec.allowFamilyV6)
        assertTrue(spec.routeAllV4)
    }

    @Test
    fun `tunSpec без IPv6 → allowFamilyV6=false`() = runTest {
        val noV6 = sampleConfig.copy(interfaceAddressV6 = "")
        val (e, _, _) = engine(activeConfig = noV6)
        val spec = e.tunSpec()!!
        assertEquals(false, spec.allowFamilyV6)
        assertNull(spec.ipv6Address)
    }

    @Test
    fun `tunSpec при register failure → null`() = runTest {
        val (e, _, _) = engine(
            activeConfig = null,
            autoConfigResult = Result.failure<RegisteredWarpConfig>(java.io.IOException("net")),
        )
        assertNull(e.tunSpec())
    }

    @Test
    fun `attachTun с rawIniOverride в slot — bridge получает raw INI без модификаций (passthrough)`() = runTest {
        val rawIni = """
            [Interface]
            PrivateKey = ${sampleConfig.privateKey}
            Address = 172.16.0.2/32
            Jc = 5
            I1 = <b 0xdeadbeef>

            [Peer]
            PublicKey = ${sampleConfig.peerPublicKey}
            Endpoint = ${sampleConfig.peerEndpoint}
        """.trimIndent()
        val bridge = FakeWarpSdkBridge()
        val (e, _, _) = engine(activeConfig = sampleConfig, activeRawIni = rawIni, bridge = bridge)
        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 7)
        assertTrue(
            bridge.lastIni!!.contains("I1 = <b 0xdeadbeef>"),
            "rawIni passthrough — I1 должен передаться в bridge",
        )
    }

    @Test
    fun `auto-register сохраняет rawIni в slot (для passthrough при следующем start)`() = runTest {
        val raw = "[Interface]\nPrivateKey = abc\n[Peer]\nPublicKey = def\nEndpoint = h:1\n"
        val (e, _, store) = engine(
            activeConfig = null,
            autoConfigResult = Result.success(RegisteredWarpConfig(sampleConfig, raw)),
        )
        e.start(EngineConfig.Warp, Upstream.None)
        assertEquals(raw, store.lastAdded?.rawIni, "auto-config rawIni сохраняется в slot")
    }

    @Test
    fun `start требует Upstream None`() = runTest {
        val (e, _, _) = engine(activeConfig = sampleConfig)
        runCatching { e.start(EngineConfig.Warp, Upstream.Socks5("127.0.0.1", 1080)) }
            .fold(
                onSuccess = { assertIs<StartResult.Failure>(it) },
                onFailure = { assertTrue(it is IllegalArgumentException) },
            )
    }

    @Test
    fun `tunSpec при ipv6Enabled=false выставляет allowFamilyV6=false и ipv6Address=null`() = runTest {
        val (e, _, _) = engineIpv6(ipv6Enabled = false)
        e.start(EngineConfig.Warp, Upstream.None)
        val spec = e.tunSpec() ?: error("tunSpec null")
        assertFalse(spec.allowFamilyV6, "allowFamilyV6 must be false when ipv6Enabled=false")
        assertNull(spec.ipv6Address, "ipv6Address must be null when ipv6Enabled=false")
    }

    @Test
    fun `tunSpec при ipv6Enabled=true сохраняет IPv6 из config`() = runTest {
        val (e, _, _) = engineIpv6(ipv6Enabled = true)
        e.start(EngineConfig.Warp, Upstream.None)
        val spec = e.tunSpec() ?: error("tunSpec null")
        assertTrue(spec.allowFamilyV6)
        assertEquals("2606:4700::1", spec.ipv6Address)
    }

    @Test
    fun `attachTun ini БЕЗ IPv6 строк при ipv6Enabled=false`() = runTest {
        val raw = """
            [Interface]
            PrivateKey = abc
            Address = 172.16.0.2/32, 2606:4700::1/128
            DNS = 1.1.1.1, 2606:4700::1
            MTU = 1280

            [Peer]
            PublicKey = X
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = 162.159.192.1:2408
        """.trimIndent()
        val bridge = FakeWarpSdkBridge()
        val (e, _, _) = engineIpv6(ipv6Enabled = false, activeRawIni = raw, bridge = bridge)
        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 42)
        val ini = bridge.lastIni ?: error("ini missing")
        assertFalse(ini.contains("::/0"), "::/0 must be removed. INI:\n$ini")
        assertFalse(
            ini.contains("2606:4700::1"),
            "IPv6 address must be removed when ipv6Enabled=false. INI:\n$ini",
        )
        assertTrue(ini.contains("0.0.0.0/0"), "IPv4 routing preserved. INI:\n$ini")
        assertTrue(ini.contains("172.16.0.2"), "IPv4 address preserved. INI:\n$ini")
    }

    @Test
    fun `attachTun ini С IPv6 при ipv6Enabled=true`() = runTest {
        val raw = """
            [Interface]
            PrivateKey = abc
            Address = 172.16.0.2/32, 2606:4700::1/128

            [Peer]
            PublicKey = X
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = 162.159.192.1:2408
        """.trimIndent()
        val bridge = FakeWarpSdkBridge()
        val (e, _, _) = engineIpv6(ipv6Enabled = true, activeRawIni = raw, bridge = bridge)
        e.start(EngineConfig.Warp, Upstream.None)
        e.attachTun(tunFd = 42)
        val ini = bridge.lastIni ?: error("ini missing")
        assertTrue(ini.contains("::/0"), "::/0 preserved when ipv6Enabled=true")
        assertTrue(ini.contains("2606:4700::1"), "IPv6 address preserved when ipv6Enabled=true")
    }

    private fun engineIpv6(
        ipv6Enabled: Boolean,
        activeConfig: WarpConfig = sampleConfig,
        activeRawIni: String? = null,
        bridge: FakeWarpSdkBridge = FakeWarpSdkBridge(),
    ): Triple<EngineWarp, FakeWarpAutoConfig, FakeWarpConfigSlotStore> {
        val store = FakeWarpConfigSlotStore(activeConfig = activeConfig, activeRawIni = activeRawIni)
        val auto = FakeWarpAutoConfig(
            Result.success(RegisteredWarpConfig(activeConfig, activeRawIni ?: "")),
        )
        val e = EngineWarp(
            autoConfig = auto,
            configStore = store,
            sdkBridge = bridge,
            uapiPathProvider = { "/tmp" },
            socketProtector = ru.ozero.enginescore.VpnSocketProtector { true },
            ipv6EnabledProvider = { ipv6Enabled },
            handshakeChecker = { _, _ -> true },
        )
        return Triple(e, auto, store)
    }

    @Test
    fun `awaitReady возвращается немедленно когда handshake уже выполнен`() = runTest {
        val calls = AtomicInteger(0)
        val (e, _, _) = engine(
            activeConfig = sampleConfig,
            handshakeChecker = { _, _ ->
                calls.incrementAndGet()
                true
            },
        )
        e.start(EngineConfig.Warp, Upstream.None)
        e.awaitReady()
        assertTrue(calls.get() >= 1, "handshakeChecker должен быть вызван хотя бы раз")
    }

    @Test
    fun `awaitReady ждёт пока handshake не завершится`() = runTest {
        val calls = AtomicInteger(0)
        val (e, _, _) = engine(
            activeConfig = sampleConfig,
            handshakeChecker = { _, _ -> calls.incrementAndGet() >= 3 },
            warpReadyTimeoutMs = 5_000L,
            warpReadyPollMs = 50L,
        )
        e.start(EngineConfig.Warp, Upstream.None)
        e.awaitReady()
        assertTrue(calls.get() >= 3, "awaitReady должен опросить хотя бы 3 раза, calls=${calls.get()}")
    }

    @Test
    fun `awaitReady завершается по таймауту — не зависает и не бросает`() = runTest {
        val (e, _, _) = engine(
            activeConfig = sampleConfig,
            handshakeChecker = { _, _ -> false },
            warpReadyTimeoutMs = 300L,
            warpReadyPollMs = 50L,
        )
        e.start(EngineConfig.Warp, Upstream.None)
        var returned = false
        try {
            e.awaitReady()
            returned = true
        } catch (_: Throwable) {
            fail("awaitReady не должен бросать исключение при таймауте")
        }
        assertTrue(returned, "awaitReady обязан вернуться после таймаута")
    }

    @Test
    fun `awaitReady при исключении из handshakeChecker — не пробрасывает`() = runTest {
        val (e, _, _) = engine(
            activeConfig = sampleConfig,
            handshakeChecker = { _, _ -> throw IllegalStateException("uapi unavailable") },
            warpReadyTimeoutMs = 300L,
            warpReadyPollMs = 50L,
        )
        e.start(EngineConfig.Warp, Upstream.None)
        var returned = false
        try {
            e.awaitReady()
            returned = true
        } catch (_: Throwable) {
            fail("awaitReady не должен пробрасывать исключения из handshakeChecker")
        }
        assertTrue(returned, "awaitReady должен вернуться даже если handshakeChecker бросает")
    }

    private class FakeWarpAutoConfig(
        private val result: Result<RegisteredWarpConfig>,
    ) : WarpAutoConfig {
        var registerCalls: Int = 0
        override suspend fun register(onProgress: ((String) -> Unit)?): Result<RegisteredWarpConfig> {
            registerCalls++
            return result
        }
    }

    data class AddedSlot(val name: String, val config: WarpConfig, val rawIni: String?)

    private class FakeWarpConfigSlotStore(
        activeConfig: WarpConfig?,
        activeRawIni: String? = null,
    ) : WarpConfigSlotStore {
        private val activeFlow = MutableStateFlow(activeConfig)
        private val slotsList = MutableStateFlow<List<WarpConfigSlot>>(emptyList())
        var lastAdded: AddedSlot? = null
            private set

        init {
            if (activeConfig != null) {
                slotsList.value = listOf(
                    WarpConfigSlot(
                        id = "init",
                        name = "Init",
                        config = activeConfig,
                        isActive = true,
                        rawIniOverride = activeRawIni,
                    ),
                )
            }
        }

        override fun slots(): Flow<List<WarpConfigSlot>> = slotsList
        override fun activeSlot(): Flow<WarpConfigSlot?> = MutableStateFlow(slotsList.value.firstOrNull { it.isActive })
        override fun activeConfig(): Flow<WarpConfig?> = activeFlow

        override suspend fun addSlot(name: String, config: WarpConfig, rawIni: String?): String {
            lastAdded = AddedSlot(name, config, rawIni)
            val id = "fake-${slotsList.value.size}"
            slotsList.value = slotsList.value +
                WarpConfigSlot(
                    id = id,
                    name = name,
                    config = config,
                    isActive = slotsList.value.isEmpty(),
                    rawIniOverride = rawIni,
                )
            activeFlow.value = config
            return id
        }

        override suspend fun setActive(id: String) {
            slotsList.value = slotsList.value.map { it.copy(isActive = it.id == id) }
            activeFlow.value = slotsList.value.firstOrNull { it.id == id }?.config
        }

        override suspend fun rename(id: String, name: String) {
            slotsList.value = slotsList.value.map { if (it.id == id) it.copy(name = name) else it }
        }

        override suspend fun updateSlot(id: String, name: String, config: WarpConfig, rawIni: String?) {
            slotsList.value = slotsList.value.map {
                if (it.id == id) it.copy(name = name, config = config, rawIniOverride = rawIni) else it
            }
        }

        override suspend fun delete(id: String) {
            slotsList.value = slotsList.value.filter { it.id != id }
        }

        override suspend fun clear() {
            slotsList.value = emptyList()
            activeFlow.value = null
        }

        override suspend fun replaceAll(slots: List<WarpConfigSlot>) {
            slotsList.value = slots
            activeFlow.value = slots.firstOrNull { it.isActive }?.config
        }
    }

    private class FakeWarpSdkBridge(
        private val attachResult: WarpSdkBridge.AttachResult = WarpSdkBridge.AttachResult.Success,
    ) : WarpSdkBridge {
        var attachCalls: Int = 0
        var detachCalls: Int = 0
        var lastFd: Int = -1
        var lastIni: String? = null
        var lastUapi: String? = null
        private var running = false

        override suspend fun attachTun(
            tunnelName: String,
            tunFd: Int,
            iniConfig: String,
            uapiPath: String,
            protector: ru.ozero.enginescore.VpnSocketProtector,
        ): WarpSdkBridge.AttachResult {
            attachCalls++
            lastFd = tunFd
            lastIni = iniConfig
            lastUapi = uapiPath
            if (attachResult is WarpSdkBridge.AttachResult.Success) running = true
            return attachResult
        }

        override suspend fun detachTun() {
            detachCalls++
            running = false
        }

        override fun isRunning(): Boolean = running
    }
}
