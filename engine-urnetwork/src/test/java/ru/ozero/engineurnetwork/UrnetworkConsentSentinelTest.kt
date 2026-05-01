package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UrnetworkConsentSentinelTest {

    @Test
    fun `EngineUrnetwork_start без consent НИКОГДА не дёргает bridge_start`() = runTest {
        val bridge = SpyBridge()
        val store = AlwaysNoConsentStore()
        val engine = EngineUrnetwork(store, bridge)

        val result = engine.start(
            EngineConfig.Urnetwork(jwtToken = ""),
            Upstream.None,
        )

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("consent", ignoreCase = true))
        assertTrue(bridge.startCalls == 0)
    }

    private class AlwaysNoConsentStore : UrnetworkConfigStore {
        private val override = MutableStateFlow<String?>(null)
        override fun walletAddress(): Flow<String> = override.map { UrnetworkDefaults.PRESET_WALLET }
        override fun walletOverride(): Flow<String?> = override
        override suspend fun setWalletOverride(value: String?) {
            override.value = value
        }
        override fun consentGranted(): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun markConsentGranted() = error("sentinel")
        override suspend fun revokeConsent() = Unit
    }

    private class SpyBridge : UrnetworkSdkBridge {
        var startCalls: Int = 0
        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
        ): UrnetworkSdkBridge.StartResult {
            startCalls++
            return UrnetworkSdkBridge.StartResult.Success
        }
        override suspend fun stop() = Unit
        override fun isRunning(): Boolean = false
    }
}
