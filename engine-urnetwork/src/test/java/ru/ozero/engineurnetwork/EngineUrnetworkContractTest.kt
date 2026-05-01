package ru.ozero.engineurnetwork

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

class EngineUrnetworkContractTest {

    private val baseConfig = EngineConfig.Urnetwork(jwtToken = "")

    private fun engine(
        consent: Boolean = true,
        override: String? = null,
        bridge: FakeUrnetworkSdkBridge = FakeUrnetworkSdkBridge(),
    ): Pair<EngineUrnetwork, FakeUrnetworkSdkBridge> {
        val store = FakeUrnetworkConfigStore(consent = consent, override = override)
        return EngineUrnetwork(store, bridge) to bridge
    }

    @Test
    fun `id равен URNETWORK`() {
        val (e, _) = engine()
        assertEquals(EngineId.URNETWORK, e.id)
    }

    @Test
    fun `start без consent возвращает Failure с consent reason`() = runTest {
        val (e, bridge) = engine(consent = false)
        val result = e.start(baseConfig, Upstream.None)
        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("consent", ignoreCase = true))
        assertEquals(0, bridge.startCalls)
    }

    @Test
    fun `start с consent и без override вызывает bridge с PRESET_WALLET`() = runTest {
        val (e, bridge) = engine(consent = true, override = null)
        val result = e.start(baseConfig, Upstream.None)
        assertIs<StartResult.Success>(result)
        assertEquals(UrnetworkDefaults.PRESET_WALLET, bridge.lastWallet)
        assertEquals(1, bridge.startCalls)
    }

    @Test
    fun `start с consent и override вызывает bridge с override адресом`() = runTest {
        val custom = "AAAAbbbbCCCCdddd1111222233334444555566667777"
        val (e, bridge) = engine(consent = true, override = custom)
        val result = e.start(baseConfig, Upstream.None)
        assertIs<StartResult.Success>(result)
        assertEquals(custom, bridge.lastWallet)
    }

    @Test
    fun `stop вызывает bridge_stop`() = runTest {
        val (e, bridge) = engine(consent = true)
        e.start(baseConfig, Upstream.None)
        e.stop()
        assertEquals(1, bridge.stopCalls)
    }

    @Test
    fun `failed bridge пробрасывается как StartResult_Failure`() = runTest {
        val bridge = FakeUrnetworkSdkBridge(
            startResult = UrnetworkSdkBridge.StartResult.Failed("AAR not built"),
        )
        val (e, _) = engine(consent = true, bridge = bridge)
        val result = e.start(baseConfig, Upstream.None)
        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("AAR not built"))
    }

    @Test
    fun `start требует Upstream_None как и ByeDpi`() = runTest {
        val (e, _) = engine(consent = true)
        runCatching {
            e.start(baseConfig, Upstream.Socks5("127.0.0.1", 1080))
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

    private class FakeUrnetworkConfigStore(
        consent: Boolean,
        override: String?,
    ) : UrnetworkConfigStore {
        private val consentFlow = MutableStateFlow(consent)
        private val overrideFlow = MutableStateFlow(override)
        override fun walletAddress(): Flow<String> =
            overrideFlow.map { it ?: UrnetworkDefaults.PRESET_WALLET }
        override fun walletOverride(): Flow<String?> = overrideFlow
        override suspend fun setWalletOverride(value: String?) {
            overrideFlow.value = value
        }
        override fun consentGranted(): Flow<Boolean> = consentFlow
        override suspend fun markConsentGranted() {
            consentFlow.value = true
        }
        override suspend fun revokeConsent() {
            consentFlow.value = false
        }
    }

    private class FakeUrnetworkSdkBridge(
        private val startResult: UrnetworkSdkBridge.StartResult = UrnetworkSdkBridge.StartResult.Success,
    ) : UrnetworkSdkBridge {
        var startCalls: Int = 0
        var stopCalls: Int = 0
        var lastWallet: String? = null
        var lastApi: String? = null
        var lastConnect: String? = null
        private var running = false

        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
        ): UrnetworkSdkBridge.StartResult {
            startCalls++
            lastWallet = walletAddress
            lastApi = apiUrl
            lastConnect = connectUrl
            if (startResult is UrnetworkSdkBridge.StartResult.Success) running = true
            return startResult
        }

        override suspend fun stop() {
            stopCalls++
            running = false
        }

        override fun isRunning(): Boolean = running
    }
}
