package ru.ozero.engineurnetwork

import com.bringyour.sdk.ConnectLocation
import com.bringyour.sdk.LocationsViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.auth.ClientJwtResult
import ru.ozero.engineurnetwork.auth.GuestJwtResult
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.EnginePlugin
import ru.ozero.enginescore.Upstream
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EngineUrnetworkRecoverTest {

    private val baseConfig = EngineConfig.Urnetwork(jwtToken = "")

    @Test
    fun `recover возвращает Failed если bridge не запущен`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val bridge = FakeRecoverBridge(running = false)
        val engine = EngineUrnetwork(
            configStore = FakeStore(byJwt = "j", byClientJwt = "cj"),
            sdkBridge = bridge,
            authService = FakeAuth(),
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
            configStore = FakeStore(byJwt = "j", byClientJwt = "cj"),
            sdkBridge = bridge,
            authService = FakeAuth(),
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

    private class FakeRecoverBridge(
        var running: Boolean,
        var location: ConnectLocation? = null,
    ) : UrnetworkSdkBridge {
        var connectToCalls: Int = 0
        var connectBestAvailableCalls: Int = 0
        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byClientJwt: String,
        ): UrnetworkSdkBridge.StartResult {
            running = true
            return UrnetworkSdkBridge.StartResult.Success
        }
        override suspend fun stop() { running = false }
        override fun isRunning(): Boolean = running
        override suspend fun attachTun(tunFd: Int) = UrnetworkSdkBridge.AttachResult.Success
        override fun connectTo(location: ConnectLocation) { connectToCalls++ }
        override fun connectBestAvailable() { connectBestAvailableCalls++ }
        override fun selectedLocation(): ConnectLocation? = location
        override fun openLocationsViewController(): LocationsViewController? = null
        override fun setProvidePaused(paused: Boolean) = Unit
        override fun isProvidePaused(): Boolean = true
        override fun peerCount(): Int = 0
        override fun unpaidByteCount(): Long = 0L
        override fun fetchTransferStats() = Unit
        override suspend fun fetchSubscriptionBalance(): UrnetworkSdkBridge.SubscriptionBalanceSnapshot? = null
    }

    private class FakeStore(
        val byJwt: String? = null,
        val byClientJwt: String? = null,
    ) : UrnetworkConfigStore {
        private val overrideFlow = MutableStateFlow<String?>(null)
        private val byJwtFlow = MutableStateFlow(byJwt)
        private val byClientJwtFlow = MutableStateFlow(byClientJwt)
        override fun walletOverride(): Flow<String?> = overrideFlow
        override fun walletAddress(): Flow<String> =
            overrideFlow.map { it ?: UrnetworkDefaults.PRESET_WALLET }
        override suspend fun setWalletOverride(value: String?) { overrideFlow.value = value }
        override fun byJwt(): Flow<String?> = byJwtFlow
        override suspend fun setByJwt(value: String?) { byJwtFlow.value = value }
        override fun byClientJwt(): Flow<String?> = byClientJwtFlow
        override suspend fun setByClientJwt(value: String?) { byClientJwtFlow.value = value }
    }

    private class FakeAuth : UrnetworkAuthService {
        override suspend fun acquireGuestJwt(): GuestJwtResult = GuestJwtResult.Success("g")
        override suspend fun acquireClientJwt(byJwt: String): ClientJwtResult = ClientJwtResult.Success("c")
    }
}
