package ru.ozero.enginewarp

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        cached: WarpConfig? = null,
        autoConfigResult: Result<WarpConfig> = Result.success(sampleConfig),
        bridge: FakeWarpSdkBridge = FakeWarpSdkBridge(),
    ): Triple<EngineWarp, FakeWarpAutoConfig, FakeWarpConfigStore> {
        val store = FakeWarpConfigStore(initial = cached)
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
    fun `start без cached config регистрирует и сохраняет`() = runTest {
        val bridge = FakeWarpSdkBridge(WarpSdkBridge.StartResult.Success)
        val (e, auto, store) = engine(cached = null, bridge = bridge)

        val result = e.start(EngineConfig.Warp, Upstream.None)

        assertIs<StartResult.Success>(result)
        assertEquals(1, auto.registerCalls)
        assertEquals(sampleConfig, store.saved)
        assertEquals(1, bridge.startCalls)
        assertEquals(sampleConfig, bridge.lastConfig)
    }

    @Test
    fun `start с cached config пропускает register`() = runTest {
        val cached = sampleConfig.copy(privateKey = "cached-priv")
        val bridge = FakeWarpSdkBridge(WarpSdkBridge.StartResult.Success)
        val (e, auto, _) = engine(cached = cached, bridge = bridge)

        val result = e.start(EngineConfig.Warp, Upstream.None)

        assertIs<StartResult.Success>(result)
        assertEquals(0, auto.registerCalls)
        assertEquals(1, bridge.startCalls)
        assertEquals(cached, bridge.lastConfig)
    }

    @Test
    fun `stop вызывает bridge_stop`() = runTest {
        val bridge = FakeWarpSdkBridge(WarpSdkBridge.StartResult.Success)
        val (e, _, _) = engine(cached = sampleConfig, bridge = bridge)
        e.start(EngineConfig.Warp, Upstream.None)
        e.stop()
        assertEquals(1, bridge.stopCalls)
    }

    @Test
    fun `register failure пробрасывается как StartResult_Failure`() = runTest {
        val (e, _, _) = engine(
            cached = null,
            autoConfigResult = Result.failure(java.io.IOException("net down")),
        )

        val result = e.start(EngineConfig.Warp, Upstream.None)

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("net down") || failure.reason.contains("register"))
    }

    @Test
    fun `bridge failure пробрасывается как StartResult_Failure`() = runTest {
        val bridge = FakeWarpSdkBridge(WarpSdkBridge.StartResult.Failed("AAR not built"))
        val (e, _, _) = engine(cached = sampleConfig, bridge = bridge)

        val result = e.start(EngineConfig.Warp, Upstream.None)

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("AAR not built"))
    }

    @Test
    fun `start требует Upstream_None`() = runTest {
        val (e, _, _) = engine(cached = sampleConfig)

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
        override suspend fun register(): Result<WarpConfig> {
            registerCalls++
            return result
        }
    }

    private class FakeWarpConfigStore(
        initial: WarpConfig?,
    ) : WarpConfigStore {
        private val flow = MutableStateFlow(initial)
        var saved: WarpConfig? = null
            private set

        override fun current(): Flow<WarpConfig?> = flow
        override suspend fun save(config: WarpConfig) {
            saved = config
            flow.value = config
        }
        override suspend fun clear() {
            flow.value = null
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
