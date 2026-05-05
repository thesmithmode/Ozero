package ru.ozero.enginewarp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EngineId
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
    ): Triple<EngineWarp, FakeWarpAutoConfig, FakeWarpConfigSlotStore> {
        val store = FakeWarpConfigSlotStore(activeConfig = activeConfig)
        val auto = FakeWarpAutoConfig(autoConfigResult)
        val e = EngineWarp(autoConfig = auto, configStore = store, sdkBridge = bridge)
        return Triple(e, auto, store)
    }

    @Test
    fun `id равен WARP`() {
        val (e, _, _) = engine()
        assertEquals(EngineId.WARP, e.id)
    }

    @Test
    fun `start без active config регистрирует и добавляет слот`() = runTest {
        val bridge = FakeWarpSdkBridge(WarpSdkBridge.StartResult.Success)
        val (e, auto, store) = engine(activeConfig = null, bridge = bridge)

        val result = e.start(EngineConfig.Warp, Upstream.None)

        assertIs<StartResult.Success>(result)
        assertEquals(1, auto.registerCalls)
        assertEquals(sampleConfig, store.lastAdded?.second)
        assertEquals(1, bridge.startCalls)
        assertEquals(sampleConfig, bridge.lastConfig)
    }

    @Test
    fun `start с active config пропускает register`() = runTest {
        val active = sampleConfig.copy(privateKey = "active-priv")
        val bridge = FakeWarpSdkBridge(WarpSdkBridge.StartResult.Success)
        val (e, auto, _) = engine(activeConfig = active, bridge = bridge)

        val result = e.start(EngineConfig.Warp, Upstream.None)

        assertIs<StartResult.Success>(result)
        assertEquals(0, auto.registerCalls)
        assertEquals(1, bridge.startCalls)
        assertEquals(active, bridge.lastConfig)
    }

    @Test
    fun `stop вызывает bridge_stop`() = runTest {
        val bridge = FakeWarpSdkBridge(WarpSdkBridge.StartResult.Success)
        val (e, _, _) = engine(activeConfig = sampleConfig, bridge = bridge)
        e.start(EngineConfig.Warp, Upstream.None)
        e.stop()
        assertEquals(1, bridge.stopCalls)
    }

    @Test
    fun `register failure пробрасывается как StartResult_Failure`() = runTest {
        val (e, _, _) = engine(
            activeConfig = null,
            autoConfigResult = Result.failure(java.io.IOException("net down")),
        )

        val result = e.start(EngineConfig.Warp, Upstream.None)

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("net down") || failure.reason.contains("register"))
    }

    @Test
    fun `bridge failure пробрасывается как StartResult_Failure`() = runTest {
        val bridge = FakeWarpSdkBridge(WarpSdkBridge.StartResult.Failed("AAR not built"))
        val (e, _, _) = engine(activeConfig = sampleConfig, bridge = bridge)

        val result = e.start(EngineConfig.Warp, Upstream.None)

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("AAR not built"))
    }

    @Test
    fun `start требует Upstream_None`() = runTest {
        val (e, _, _) = engine(activeConfig = sampleConfig)

        runCatching {
            e.start(EngineConfig.Warp, Upstream.Socks5("127.0.0.1", 1080))
        }.fold(
            onSuccess = { result ->
                val failure = assertIs<StartResult.Failure>(result)
                assertTrue(failure.reason.contains("upstream", ignoreCase = true))
            },
            onFailure = {
                assertTrue(it is IllegalArgumentException)
            },
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
                    WarpConfigSlot(id = "initial", name = "Initial", config = activeConfig, isActive = true),
                )
            }
        }

        override fun slots(): Flow<List<WarpConfigSlot>> = slotsList
        override fun activeConfig(): Flow<WarpConfig?> = activeFlow

        override suspend fun addSlot(name: String, config: WarpConfig): String {
            lastAdded = name to config
            val id = "fake-${slotsList.value.size}"
            val slot = WarpConfigSlot(id = id, name = name, config = config, isActive = slotsList.value.isEmpty())
            slotsList.value = slotsList.value + slot
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
            if (activeFlow.value != null && slotsList.value.none { it.isActive }) {
                activeFlow.value = null
            }
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
        private val startResult: WarpSdkBridge.StartResult = WarpSdkBridge.StartResult.Success,
    ) : WarpSdkBridge {
        var startCalls: Int = 0
        var stopCalls: Int = 0
        var lastConfig: WarpConfig? = null
        private var running = false

        override suspend fun start(config: WarpConfig): WarpSdkBridge.StartResult {
            startCalls++
            lastConfig = config
            if (startResult is WarpSdkBridge.StartResult.Success) running = true
            return startResult
        }

        override suspend fun stop() {
            stopCalls++
            running = false
        }

        override fun isRunning(): Boolean = running
    }
}
