package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.auth.GuestJwtResult
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UrnetworkConsentSentinelTest {

    @Test
    fun `EngineUrnetwork_start без consent auto-grants и продолжает (guest mode — нет явного юзер-данных)`() = runTest {
        val bridge = SpyBridge()
        val auth = SpyAuthService()
        val store = SpyConsentStore()
        val engine = EngineUrnetwork(store, bridge, auth)

        engine.start(EngineConfig.Urnetwork(jwtToken = ""), Upstream.None)

        assertEquals(1, store.grantCalls, "Auto-grant должен сработать ровно один раз на первый connect")
        assertEquals(1, auth.acquireCalls, "Auth вызывается после auto-grant")
        assertEquals(1, bridge.startCalls, "Bridge стартует после получения guest JWT")
    }

    @Test
    fun `EngineUrnetwork_start с уже выданным consent не вызывает повторный grant`() = runTest {
        val bridge = SpyBridge()
        val auth = SpyAuthService()
        val store = SpyConsentStore(initialConsent = true)
        val engine = EngineUrnetwork(store, bridge, auth)

        engine.start(EngineConfig.Urnetwork(jwtToken = ""), Upstream.None)

        assertEquals(0, store.grantCalls, "Повторный grant не должен вызываться если consent уже есть")
        assertEquals(1, bridge.startCalls)
    }

    private class SpyAuthService : UrnetworkAuthService {
        var acquireCalls: Int = 0
        override suspend fun acquireGuestJwt(): GuestJwtResult {
            acquireCalls++
            return GuestJwtResult.Success("spy.jwt")
        }
    }

    private class SpyConsentStore(initialConsent: Boolean = false) : UrnetworkConfigStore {
        private val walletOverrideState = MutableStateFlow<String?>(null)
        private val consentState = MutableStateFlow(initialConsent)
        private val jwtState = MutableStateFlow<String?>(null)
        var grantCalls: Int = 0

        override fun walletAddress(): Flow<String> = walletOverrideState.map { UrnetworkDefaults.PRESET_WALLET }
        override fun walletOverride(): Flow<String?> = walletOverrideState
        override suspend fun setWalletOverride(value: String?) { walletOverrideState.value = value }
        override fun consentGranted(): Flow<Boolean> = consentState
        override suspend fun markConsentGranted() { grantCalls++; consentState.value = true }
        override suspend fun revokeConsent() { consentState.value = false }
        override fun byJwt(): Flow<String?> = jwtState
        override suspend fun setByJwt(value: String?) { jwtState.value = value }
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
