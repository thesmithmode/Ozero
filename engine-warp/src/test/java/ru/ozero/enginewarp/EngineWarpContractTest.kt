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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineWarpContractTest {

    private val sampleConfig = WarpConfig(
        privateKey = "p",
        publicKey = "P",
        peerPublicKey = "PP",
        peerEndpoint = "engage.cloudflareclient.com:2408",
        interfaceAddressV4 = "172.16.0.2/32",
        interfaceAddressV6 = "2606:4700::1/128",
        accountLicense = "L",
    )

    private fun engine(
        activeConfig: WarpConfig? = null,
        autoConfigResult: Result<WarpConfig> = Result.success(sampleConfig),
        bridge: FakeWarpSdkBridge = FakeWarpSdkBridge(),
        uapiPath: String = "/data/data/ru.ozero.app",
    ): Triple<EngineWarp, FakeWarpAutoConfig, FakeWarpConfigSlotStore> {
        val store = FakeWarpConfigSlotStore(activeConfig = activeConfig)
        val auto = FakeWarpAutoConfig(autoConfigResult)
        val e = EngineWarp(
            autoConfig = auto,
            configStore = store,
            sdkBridge = bridge,
            uapiPathProvider = { uapiPath },
            socketProtector = ru.ozero.enginescore.VpnSocketProtector { true },
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
        assertEquals(sampleConfig, store.lastAdded?.second)
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
            autoConfigResult = Result.failure(java.io.IOException("net down")),
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
            autoConfigResult = Result.failure(java.io.IOException("net")),
        )
        assertNull(e.tunSpec())
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

    private class FakeWarpAutoConfig(
        private val result: Result<WarpConfig>,
    ) : WarpAutoConfig {
        var registerCalls: Int = 0
        override suspend fun register(onProgress: ((String) -> Unit)?): Result<WarpConfig> {
            registerCalls++
            return result
        }
    }

    private class FakeWarpConfigSlotStore(
        activeConfig: WarpConfig?,
    ) : WarpConfigSlotStore {
        private val activeFlow = MutableStateFlow(activeConfig)
        private val slotsList = MutableStateFlow<List<WarpConfigSlot>>(emptyList())
        var lastAdded: Pair<String, WarpConfig>? = null
            private set

        init {
            if (activeConfig != null) {
                slotsList.value = listOf(
                    WarpConfigSlot(id = "init", name = "Init", config = activeConfig, isActive = true),
                )
            }
        }

        override fun slots(): Flow<List<WarpConfigSlot>> = slotsList
        override fun activeConfig(): Flow<WarpConfig?> = activeFlow

        override suspend fun addSlot(name: String, config: WarpConfig): String {
            lastAdded = name to config
            val id = "fake-${slotsList.value.size}"
            slotsList.value = slotsList.value +
                WarpConfigSlot(id = id, name = name, config = config, isActive = slotsList.value.isEmpty())
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
