package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.auth.GuestJwtResult
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UrnetworkConsentSentinelTest {

    @Test
    fun `EngineUrnetwork_start без JWT auto-acquire guest и стартует bridge`() = runTest {
        val bridge = SpyBridge()
        val auth = SpyAuthService()
        val store = SpyStore()
        val engine = EngineUrnetwork(store, bridge, auth)

        engine.start(EngineConfig.Urnetwork(jwtToken = ""), Upstream.None)

        assertEquals(1, auth.acquireCalls, "Guest JWT должен быть запрошен при отсутствии в store")
        assertEquals(1, bridge.startCalls, "Bridge должен стартовать после получения JWT")
    }

    @Test
    fun `EngineUrnetwork_start с существующим JWT не вызывает повторный acquire`() = runTest {
        val bridge = SpyBridge()
        val auth = SpyAuthService()
        val store = SpyStore(existingJwt = "existing.jwt")
        val engine = EngineUrnetwork(store, bridge, auth)

        val result = engine.start(EngineConfig.Urnetwork(jwtToken = ""), Upstream.None)

        assertIs<StartResult.Success>(result)
        assertEquals(0, auth.acquireCalls, "Повторный acquire не нужен если JWT уже есть")
        assertEquals(1, bridge.startCalls)
    }

    private class SpyAuthService : UrnetworkAuthService {
        var acquireCalls: Int = 0
        override suspend fun acquireGuestJwt(): GuestJwtResult {
            acquireCalls++
            return GuestJwtResult.Success("spy.jwt")
        }
    }

    private class SpyStore(existingJwt: String? = null) : UrnetworkConfigStore {
        private val jwtFlow = MutableStateFlow(existingJwt)
        override fun walletAddress(): Flow<String> = MutableStateFlow(UrnetworkDefaults.PRESET_WALLET)
        override fun walletOverride(): Flow<String?> = MutableStateFlow(null)
        override suspend fun setWalletOverride(value: String?) = Unit
        override fun byJwt(): Flow<String?> = jwtFlow
        override suspend fun setByJwt(value: String?) { jwtFlow.value = value }
    }

    private class SpyBridge : UrnetworkSdkBridge {
        var startCalls: Int = 0
        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byJwt: String?,
        ): UrnetworkSdkBridge.StartResult {
            startCalls++
            return UrnetworkSdkBridge.StartResult.Success
        }
        override suspend fun stop() = Unit
        override fun isRunning(): Boolean = false
        override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult =
            UrnetworkSdkBridge.AttachResult.Success
    }
}
