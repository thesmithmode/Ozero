package ru.ozero.engineurnetwork

import com.bringyour.sdk.LocationsViewController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.ozero.engineurnetwork.auth.ClientJwtResult
import ru.ozero.engineurnetwork.auth.GuestJwtResult
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.Upstream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EngineUrnetworkRecoverTest {

    private val baseConfig = EngineConfig.Urnetwork(jwtToken = "")

    @Test
    fun `recover возвращает Failed если bridge не запущен`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val bridge = FakeRecoverBridge(running = false)
        val engine = EngineUrnetwork(
            configStore = fakeStore(byJwt = "j", byClientJwt = "cj"),
            sdkBridge = bridge,
            authService = FakeAuth(),
            deviceIdentity = null,
            pluginScope = scope,
        )
        val result = engine.recover()
        assertIs<EnginePlugin.RecoverResult.Failed>(result)
        scope.cancel()
    }

    @Test
    fun `recover вызывает connectBestAvailable когда selectedLocation null`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val bridge = FakeRecoverBridge(running = true, location = null)
        val engine = EngineUrnetwork(
            configStore = fakeStore(byJwt = "j", byClientJwt = "cj"),
            sdkBridge = bridge,
            authService = FakeAuth(),
            deviceIdentity = null,
            pluginScope = scope,
        )
        engine.start(baseConfig, Upstream.None)
        runCurrent()
        val result = engine.recover()
        assertIs<EnginePlugin.RecoverResult.Success>(result)
        assertEquals(1, bridge.connectBestAvailableCalls)
        assertEquals(0, bridge.connectToCalls)
        scope.cancel()
    }

    @Test
    fun `recover вызывает connectTo когда selectedLocation не null`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val fakeLocation = FakeLocation()
        val bridge = FakeRecoverBridge(running = true, location = fakeLocation)
        val engine = EngineUrnetwork(
            configStore = fakeStore(byJwt = "j", byClientJwt = "cj"),
            sdkBridge = bridge,
            authService = FakeAuth(),
            deviceIdentity = null,
            pluginScope = scope,
        )
        engine.start(baseConfig, Upstream.None)
        runCurrent()
        val result = engine.recover()
        assertIs<EnginePlugin.RecoverResult.Success>(result)
        assertEquals(1, bridge.connectToCalls)
        assertEquals(0, bridge.connectBestAvailableCalls)
        assertEquals(fakeLocation, bridge.lastConnectToLocation)
        scope.cancel()
    }

    @Test
    fun `recover пробрасывает CancellationException — не глотает coroutine cancel`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val bridge = FakeRecoverBridge(
            running = true,
            location = FakeLocation(),
            throwOnConnect = CancellationException("upstream cancel"),
        )
        val engine = EngineUrnetwork(
            configStore = fakeStore(byJwt = "j", byClientJwt = "cj"),
            sdkBridge = bridge,
            authService = FakeAuth(),
            deviceIdentity = null,
            pluginScope = scope,
        )
        engine.start(baseConfig, Upstream.None)
        runCurrent()
        assertThrows<CancellationException> {
            engine.recover()
        }
        scope.cancel()
    }

    @Test
    fun `recover возвращает Failed когда bridge connectTo бросает исключение`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val bridge = FakeRecoverBridge(
            running = true,
            location = FakeLocation(),
            throwOnConnect = RuntimeException("sdk transient"),
        )
        val engine = EngineUrnetwork(
            configStore = fakeStore(byJwt = "j", byClientJwt = "cj"),
            sdkBridge = bridge,
            authService = FakeAuth(),
            deviceIdentity = null,
            pluginScope = scope,
        )
        engine.start(baseConfig, Upstream.None)
        runCurrent()
        val result = engine.recover()
        assertIs<EnginePlugin.RecoverResult.Failed>(result)
        assertTrue(result.reason.contains("sdk transient"), "reason должен содержать причину: ${result.reason}")
        scope.cancel()
    }

    private data class FakeLocation(override val countryCode: String? = null) : UrnetworkSdkBridge.LocationToken

    private class FakeRecoverBridge(
        var running: Boolean,
        var location: UrnetworkSdkBridge.LocationToken? = null,
        val throwOnConnect: Throwable? = null,
    ) : UrnetworkSdkBridge {
        var connectToCalls: Int = 0
        var connectBestAvailableCalls: Int = 0
        var lastConnectToLocation: UrnetworkSdkBridge.LocationToken? = null

        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byClientJwt: String,
        ): UrnetworkSdkBridge.StartResult {
            running = true
            return UrnetworkSdkBridge.StartResult.Success
        }

        override suspend fun stop() {
            running = false
        }

        override fun isRunning(): Boolean = running

        override suspend fun attachTun(tunFd: Int) = UrnetworkSdkBridge.AttachResult.Success

        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) {
            connectToCalls++
            lastConnectToLocation = location
            throwOnConnect?.let { throw it }
        }

        override fun connectBestAvailable() {
            connectBestAvailableCalls++
            throwOnConnect?.let { throw it }
        }

        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = location

        override fun openLocationsViewController(): LocationsViewController? = null

        override fun setProvidePaused(paused: Boolean) = Unit

        override fun isProvidePaused(): Boolean = true

        override fun peerCount(): Int = 0

        override fun unpaidByteCount(): Long = 0L

        override fun fetchTransferStats() = Unit

        override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
    }

    private fun fakeStore(
        byJwt: String? = null,
        byClientJwt: String? = null,
    ): UrnetworkConfigStore =
        InMemoryUrnetworkConfigStore(UrnetworkConfig(byJwt = byJwt, byClientJwt = byClientJwt))

    private class FakeAuth : UrnetworkAuthService {
        override suspend fun acquireGuestJwt(): GuestJwtResult = GuestJwtResult.Success("g")

        override suspend fun acquireClientJwt(byJwt: String): ClientJwtResult = ClientJwtResult.Success("c")
    }
}
