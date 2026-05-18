package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.auth.ClientJwtResult
import ru.ozero.engineurnetwork.auth.DeviceWalletJwtResult
import ru.ozero.engineurnetwork.auth.GuestJwtResult
import ru.ozero.engineurnetwork.auth.InMemoryUrnetworkDeviceIdentity
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.engineurnetwork.auth.UrnetworkDeviceIdentity
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.StartResult
import ru.ozero.enginescore.Upstream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineUrnetworkDeviceJwtTest {

    @Test
    fun `device walletAuth Success → byJwt и devicePubkey сохраняются — guest не вызывается`() = runTest {
        val store = InMemoryUrnetworkConfigStore()
        val auth = RecordingAuthService(
            walletResult = DeviceWalletJwtResult.Success("device-jwt", isNewNetwork = true),
            clientResult = ClientJwtResult.Success("device-client-jwt"),
        )
        val identity = InMemoryUrnetworkDeviceIdentity(SEED_A)
        val engine = newEngine(store, auth, identity)

        val r = engine.start(EngineConfig.Urnetwork(jwtToken = "", region = null), Upstream.None)
        assertTrue(r is StartResult.Success, "start не должен упасть на in-memory bridge")

        assertEquals("device-jwt", store.config().first().byJwt)
        assertEquals(identity.pubkeyBase58(), store.config().first().devicePubkey)
        assertEquals(0, auth.guestCalls.get(), "guest НЕ должен вызываться когда walletAuth success")
        assertEquals(1, auth.walletCalls.get())
    }

    @Test
    fun `existing byJwt + devicePubkey set → ни walletAuth ни guest не вызываются — idempotent`() = runTest {
        val store = InMemoryUrnetworkConfigStore(
            UrnetworkConfig(byJwt = "saved", byClientJwt = "saved-cjwt", devicePubkey = "pk"),
        )
        val auth = RecordingAuthService()
        val identity = InMemoryUrnetworkDeviceIdentity(SEED_A)
        val engine = newEngine(store, auth, identity)

        engine.start(EngineConfig.Urnetwork(jwtToken = "", region = null), Upstream.None)

        assertEquals(0, auth.walletCalls.get(), "при device byJwt + pubkey walletAuth не зовётся")
        assertEquals(0, auth.guestCalls.get(), "при device byJwt + pubkey guest не зовётся")
    }

    @Test
    fun `legacy guest byJwt без devicePubkey → walletAuth migration — заменяет byJwt и сбрасывает byClientJwt`() =
        runTest {
            val store = InMemoryUrnetworkConfigStore(
                UrnetworkConfig(byJwt = "legacy-guest", byClientJwt = "legacy-cjwt"),
            )
            val auth = RecordingAuthService(
                walletResult = DeviceWalletJwtResult.Success("device-jwt-migrated", isNewNetwork = true),
                clientResult = ClientJwtResult.Success("new-cjwt"),
            )
            val identity = InMemoryUrnetworkDeviceIdentity(SEED_A)
            val engine = newEngine(store, auth, identity)

            engine.start(EngineConfig.Urnetwork(jwtToken = "", region = null), Upstream.None)

            val cfg = store.config().first()
            assertEquals("device-jwt-migrated", cfg.byJwt, "byJwt заменён на device-jwt")
            assertEquals(identity.pubkeyBase58(), cfg.devicePubkey)
            assertEquals(
                "new-cjwt",
                cfg.byClientJwt,
                "byClientJwt должен быть инвалидирован миграцией и переакваэрен",
            )
            assertEquals(1, auth.walletCalls.get())
        }

    @Test
    fun `legacy guest byJwt + walletAuth Error → existing byJwt сохраняется, guest НЕ вызывается заново`() = runTest {
        val store = InMemoryUrnetworkConfigStore(UrnetworkConfig(byJwt = "legacy", byClientJwt = "legacy-cjwt"))
        val auth = RecordingAuthService(walletResult = DeviceWalletJwtResult.Error("server down"))
        val identity = InMemoryUrnetworkDeviceIdentity(SEED_A)
        val engine = newEngine(store, auth, identity)

        engine.start(EngineConfig.Urnetwork(jwtToken = "", region = null), Upstream.None)

        val cfg = store.config().first()
        assertEquals("legacy", cfg.byJwt, "при failed migration существующий byJwt должен остаться")
        assertNull(cfg.devicePubkey)
        assertEquals(1, auth.walletCalls.get())
        assertEquals(0, auth.guestCalls.get(), "guest НЕ должен повторно вызываться когда уже есть legacy byJwt")
    }

    @Test
    fun `device walletAuth Error → fallback на guest, devicePubkey остаётся null`() = runTest {
        val store = InMemoryUrnetworkConfigStore()
        val auth = RecordingAuthService(
            walletResult = DeviceWalletJwtResult.Error("server boom"),
            guestResult = GuestJwtResult.Success("guest-jwt"),
            clientResult = ClientJwtResult.Success("guest-client-jwt"),
        )
        val identity = InMemoryUrnetworkDeviceIdentity(SEED_A)
        val engine = newEngine(store, auth, identity)

        engine.start(EngineConfig.Urnetwork(jwtToken = "", region = null), Upstream.None)

        val cfg = store.config().first()
        assertEquals("guest-jwt", cfg.byJwt)
        assertNull(cfg.devicePubkey, "devicePubkey не должен set'иться при error walletAuth")
        assertEquals(1, auth.walletCalls.get())
        assertEquals(1, auth.guestCalls.get())
    }

    @Test
    fun `identity null → walletAuth не вызывается, guest сразу`() = runTest {
        val store = InMemoryUrnetworkConfigStore()
        val auth = RecordingAuthService(guestResult = GuestJwtResult.Success("g"))
        val engine = newEngine(store, auth, identity = null)

        engine.start(EngineConfig.Urnetwork(jwtToken = "", region = null), Upstream.None)

        assertEquals(0, auth.walletCalls.get(), "identity=null значит skip walletAuth полностью")
        assertEquals(1, auth.guestCalls.get())
    }

    @Test
    fun `сохранённый networkName переиспользуется — повторный walletAuth не пересоздаёт network`() = runTest {
        val store = InMemoryUrnetworkConfigStore(UrnetworkConfig(deviceNetworkName = "n-cached"))
        val auth = RecordingAuthService(
            walletResult = DeviceWalletJwtResult.Success("device-jwt", isNewNetwork = false),
            clientResult = ClientJwtResult.Success("cjwt"),
        )
        val identity = InMemoryUrnetworkDeviceIdentity(SEED_A)
        val engine = newEngine(store, auth, identity)

        engine.start(EngineConfig.Urnetwork(jwtToken = "", region = null), Upstream.None)

        assertEquals("n-cached", auth.lastNetworkName)
        assertEquals("n-cached", store.config().first().deviceNetworkName)
    }

    @Test
    fun `новый networkName генератор используется когда stored пустой`() = runTest {
        val store = InMemoryUrnetworkConfigStore()
        val auth = RecordingAuthService(
            walletResult = DeviceWalletJwtResult.Success("j", isNewNetwork = true),
            clientResult = ClientJwtResult.Success("cj"),
        )
        val identity = InMemoryUrnetworkDeviceIdentity(SEED_A)
        val engine = EngineUrnetwork(
            configStore = store,
            sdkBridge = SuccessBridge(),
            authService = auth,
            deviceIdentity = identity,
            networkNameGenerator = { "fixed-name" },
        )

        engine.start(EngineConfig.Urnetwork(jwtToken = "", region = null), Upstream.None)

        assertEquals("fixed-name", auth.lastNetworkName)
        assertEquals("fixed-name", store.config().first().deviceNetworkName)
    }

    private fun newEngine(
        store: InMemoryUrnetworkConfigStore,
        auth: UrnetworkAuthService,
        identity: UrnetworkDeviceIdentity?,
    ): EngineUrnetwork = EngineUrnetwork(
        configStore = store,
        sdkBridge = SuccessBridge(),
        authService = auth,
        deviceIdentity = identity,
        networkNameGenerator = { "n-test" },
    )

    private class RecordingAuthService(
        private val walletResult: DeviceWalletJwtResult =
            DeviceWalletJwtResult.Error("not configured"),
        private val guestResult: GuestJwtResult = GuestJwtResult.Error("not configured"),
        private val clientResult: ClientJwtResult = ClientJwtResult.Error("not configured"),
    ) : UrnetworkAuthService {
        val walletCalls = AtomicInteger()
        val guestCalls = AtomicInteger()
        val clientCalls = AtomicInteger()

        @Volatile
        var lastNetworkName: String? = null

        override suspend fun acquireGuestJwt(): GuestJwtResult {
            guestCalls.incrementAndGet()
            return guestResult
        }

        override suspend fun acquireClientJwt(byJwt: String): ClientJwtResult {
            clientCalls.incrementAndGet()
            assertNotNull(byJwt)
            return clientResult
        }

        override suspend fun acquireDeviceWalletJwt(
            identity: UrnetworkDeviceIdentity,
            networkName: String,
        ): DeviceWalletJwtResult {
            walletCalls.incrementAndGet()
            lastNetworkName = networkName
            return walletResult
        }
    }

    private class SuccessBridge : UrnetworkSdkBridge {
        override suspend fun start(
            walletAddress: String,
            apiUrl: String,
            connectUrl: String,
            byClientJwt: String,
        ): UrnetworkSdkBridge.StartResult = UrnetworkSdkBridge.StartResult.Success

        override suspend fun stop() = Unit
        override fun isRunning(): Boolean = true
        override suspend fun attachTun(tunFd: Int): UrnetworkSdkBridge.AttachResult =
            UrnetworkSdkBridge.AttachResult.Success

        override fun connectTo(location: UrnetworkSdkBridge.LocationToken) = Unit
        override fun connectBestAvailable() = Unit
        override fun selectedLocation(): UrnetworkSdkBridge.LocationToken? = null
        override fun openLocationsViewController() = null
        override fun setProvidePaused(paused: Boolean) = Unit
        override fun isProvidePaused(): Boolean = false
        override fun peerCount(): Int = 1
        override fun unpaidByteCount(): Long = 0L
        override fun fetchTransferStats() = Unit
        override suspend fun fetchSubscriptionBalance() = null
    }

    private companion object {
        val SEED_A = ByteArray(32) { (it + 1).toByte() }
    }
}
