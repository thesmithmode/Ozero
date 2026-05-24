package ru.ozero.enginewarp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import ru.ozero.enginescore.VpnSocketProtector
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EngineWarpEndpointCyclingTest {

    private val baseConfig = WarpConfig(
        privateKey = "p",
        publicKey = "P",
        peerPublicKey = "PP",
        peerEndpoint = "1.0.0.1:2408",
        interfaceAddressV4 = "172.16.0.2/32",
        interfaceAddressV6 = "2606:4700::1/128",
    )

    private fun makeProber(results: Map<String, Long>): WarpEndpointProber =
        object : WarpEndpointProber() {
            override suspend fun probe(endpoints: List<String>): List<ProbeResult> =
                endpoints.map { ProbeResult(it, results[it] ?: Long.MAX_VALUE) }
                    .sortedBy { it.rttMs }
        }

    private fun makeStore(slot: WarpConfigSlot): WarpConfigSlotStore =
        object : WarpConfigSlotStore {
            override fun slots(): Flow<List<WarpConfigSlot>> = MutableStateFlow(listOf(slot))
            override fun activeSlot(): Flow<WarpConfigSlot?> = MutableStateFlow(slot)
            override fun activeConfig(): Flow<WarpConfig?> = MutableStateFlow(slot.config)
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

    @Test
    fun `probe выбирает живой эндпоинт когда первый мёртв`() = runTest {
        val endpoints = listOf("1.0.0.1:2408", "1.0.0.2:2408", "1.0.0.3:2408")
        val slot = WarpConfigSlot(
            id = "id",
            name = "Ultra",
            config = baseConfig.copy(peerEndpoint = endpoints.first()),
            isActive = true,
            endpointList = endpoints,
        )
        val prober = makeProber(
            mapOf("1.0.0.1:2408" to Long.MAX_VALUE, "1.0.0.2:2408" to 42L, "1.0.0.3:2408" to 100L),
        )
        var attachedIni: String? = null
        val engine = EngineWarp(
            autoConfig = FakeAutoConfig(),
            configStore = makeStore(slot),
            sdkBridge = FakeBridge { attachedIni = it },
            uapiPathProvider = { "/tmp" },
            endpointProber = prober,
        )
        val result = engine.start(EngineConfig.Warp, Upstream.None)
        assertIs<StartResult.Success>(result)
        engine.tunSpec()
        engine.attachTun(42)
        assertEquals("1.0.0.2:2408", attachedIni?.lines()
            ?.firstOrNull { it.trimStart().startsWith("Endpoint") }
            ?.substringAfter('=')?.trim())
    }

    @Test
    fun `probe все мёртвы — fallback на первый из списка`() = runTest {
        val endpoints = listOf("1.0.0.1:2408", "1.0.0.2:2408")
        val slot = WarpConfigSlot(
            id = "id",
            name = "Ultra",
            config = baseConfig.copy(peerEndpoint = endpoints.first()),
            isActive = true,
            endpointList = endpoints,
        )
        val prober = makeProber(emptyMap())
        var attachedIni: String? = null
        val engine = EngineWarp(
            autoConfig = FakeAutoConfig(),
            configStore = makeStore(slot),
            sdkBridge = FakeBridge { attachedIni = it },
            uapiPathProvider = { "/tmp" },
            endpointProber = prober,
        )
        engine.start(EngineConfig.Warp, Upstream.None)
        engine.tunSpec()
        engine.attachTun(42)
        assertEquals(endpoints.first(), attachedIni?.lines()
            ?.firstOrNull { it.trimStart().startsWith("Endpoint") }
            ?.substringAfter('=')?.trim())
    }

    @Test
    fun `endpointList пуст — probe не вызывается, config используется как есть`() = runTest {
        val probeCalls = mutableListOf<List<String>>()
        val prober = object : WarpEndpointProber() {
            override suspend fun probe(endpoints: List<String>): List<ProbeResult> {
                probeCalls.add(endpoints)
                return emptyList()
            }
        }
        val slot = WarpConfigSlot(
            id = "id",
            name = "Manual",
            config = baseConfig,
            isActive = true,
            endpointList = emptyList(),
        )
        val engine = EngineWarp(
            autoConfig = FakeAutoConfig(),
            configStore = makeStore(slot),
            sdkBridge = FakeBridge(),
            uapiPathProvider = { "/tmp" },
            endpointProber = prober,
        )
        engine.start(EngineConfig.Warp, Upstream.None)
        assertEquals(0, probeCalls.size, "probe не должен вызываться при пустом endpointList")
    }

    @Test
    fun `stop сбрасывает кеш и следующий start заново пробирует`() = runTest {
        val endpoints = listOf("1.0.0.1:2408", "1.0.0.2:2408")
        var probeCallCount = 0
        val prober = object : WarpEndpointProber() {
            override suspend fun probe(endpoints: List<String>): List<ProbeResult> {
                probeCallCount++
                return endpoints.map { ProbeResult(it, 10L) }.sortedBy { it.rttMs }
            }
        }
        val slot = WarpConfigSlot(
            id = "id",
            name = "Ultra",
            config = baseConfig.copy(peerEndpoint = endpoints.first()),
            isActive = true,
            endpointList = endpoints,
        )
        val engine = EngineWarp(
            autoConfig = FakeAutoConfig(),
            configStore = makeStore(slot),
            sdkBridge = FakeBridge(),
            uapiPathProvider = { "/tmp" },
            endpointProber = prober,
        )
        engine.start(EngineConfig.Warp, Upstream.None)
        assertEquals(1, probeCallCount)
        engine.stop()
        engine.start(EngineConfig.Warp, Upstream.None)
        assertEquals(2, probeCallCount, "после stop() кеш сброшен — probe вызывается снова")
    }

    private class FakeAutoConfig : WarpAutoConfig {
        override suspend fun register(onProgress: ((String) -> Unit)?): Result<RegisteredWarpConfig> =
            Result.failure(IllegalStateException("should not be called"))
    }

    private class FakeBridge(private val onAttach: (String) -> Unit = {}) : WarpSdkBridge {
        override suspend fun attachTun(
            tunnelName: String,
            tunFd: Int,
            iniConfig: String,
            uapiPath: String,
            protector: VpnSocketProtector,
        ): WarpSdkBridge.AttachResult {
            onAttach(iniConfig)
            return WarpSdkBridge.AttachResult.Success
        }
        override suspend fun detachTun() = Unit
        override fun isRunning() = false
        override fun reprotectSockets() = Unit
    }
}
