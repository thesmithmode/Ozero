package ru.ozero.engineurnetwork

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.auth.ClientJwtResult
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
    fun `EngineUrnetwork_start без consent fail-closed до auth и bridge`() = runTest {
        val bridge = SpyBridge()
        val auth = SpyAuthService()
        val store = spyStore(consentGranted = false)
        val engine = EngineUrnetwork(store, bridge, RealUrnetworkJwtBootstrapper(store, auth, null))

        val result = engine.start(EngineConfig.Urnetwork(jwtToken = ""), Upstream.None)

        val failure = assertIs<StartResult.Failure>(result)
        assertTrue(failure.reason.contains("consent", ignoreCase = true))
        assertEquals(0, auth.acquireGuestCalls)
        assertEquals(0, auth.acquireClientCalls)
        assertEquals(0, bridge.startCalls)
    }

    @Test
    fun `EngineUrnetwork_start с consent без JWT auto-acquire guest+client и стартует bridge`() = runTest {
        val bridge = SpyBridge()
        val auth = SpyAuthService()
        val store = spyStore(consentGranted = true)
        val engine = EngineUrnetwork(store, bridge, RealUrnetworkJwtBootstrapper(store, auth, null))

        val result = engine.start(EngineConfig.Urnetwork(jwtToken = ""), Upstream.None)

        assertIs<StartResult.Success>(result)
        assertEquals(1, auth.acquireGuestCalls)
        assertEquals(1, auth.acquireClientCalls)
        assertEquals(1, bridge.startCalls)
    }

    @Test
    fun `EngineUrnetwork_start с consent и существующими jwt не повторяет acquire`() = runTest {
        val bridge = SpyBridge()
        val auth = SpyAuthService()
        val store = spyStore(consentGranted = true, existingJwt = "existing.jwt", existingClientJwt = "existing.cjwt")
        val engine = EngineUrnetwork(store, bridge, RealUrnetworkJwtBootstrapper(store, auth, null))

        val result = engine.start(EngineConfig.Urnetwork(jwtToken = ""), Upstream.None)

        assertIs<StartResult.Success>(result)
        assertEquals(0, auth.acquireGuestCalls)
        assertEquals(0, auth.acquireClientCalls)
        assertEquals(1, bridge.startCalls)
    }

    private class SpyAuthService : UrnetworkAuthService {
        var acquireGuestCalls: Int = 0
        var acquireClientCalls: Int = 0
        override suspend fun acquireGuestJwt(): GuestJwtResult {
            acquireGuestCalls++
            return GuestJwtResult.Success("spy.jwt")
        }
        override suspend fun acquireClientJwt(byJwt: String): ClientJwtResult {
            acquireClientCalls++
            return ClientJwtResult.Success("spy.cjwt")
        }
    }

    private fun spyStore(
        consentGranted: Boolean,
        existingJwt: String? = null,
        existingClientJwt: String? = null,
    ): UrnetworkConfigStore =
        InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byJwt = existingJwt,
                byClientJwt = existingClientJwt,
                consentGranted = consentGranted,
            ),
        )

    private class SpyBridge : UrnetworkSdkBridge {
        var startCalls: Int = 0
        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byClientJwt: String,
        ): UrnetworkSdkBridge.StartResult {
            startCalls++
            return UrnetworkSdkBridge.StartResult.Success
        }
        override suspend fun stop() = Unit
        override fun isRunning(): Boolean = false
        override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult =
            UrnetworkSdkBridge.AttachResult.Success
        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit
        override fun connectBestAvailable() = Unit
        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = null
        override fun openLocationsViewController(): com.bringyour.sdk.LocationsViewController? = null
        override fun setProvidePaused(paused: Boolean) = Unit
        override fun isProvidePaused(): Boolean = true
        override fun peerCount(): Int = 0
        override fun unpaidByteCount(): Long = 0L
        override fun fetchTransferStats() = Unit
        override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
    }
}
