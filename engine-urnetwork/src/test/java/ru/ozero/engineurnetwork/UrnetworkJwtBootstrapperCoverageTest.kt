package ru.ozero.engineurnetwork

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.engineurnetwork.auth.ClientJwtResult
import ru.ozero.engineurnetwork.auth.DeviceWalletJwtResult
import ru.ozero.engineurnetwork.auth.GuestJwtResult
import ru.ozero.engineurnetwork.auth.UrnetworkAuthService
import ru.ozero.engineurnetwork.auth.UrnetworkDeviceIdentity
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UrnetworkJwtBootstrapperCoverageTest {

    @Test
    fun alreadyPresentClientJwtSkipsGuestClientAndDeviceAuth() = runTest {
        val store = InMemoryUrnetworkConfigStore(
            UrnetworkConfig(byJwt = "by", byClientJwt = "client", devicePubkey = "pub"),
        )
        val auth = RecordingAuth()
        val result = bootstrapper(store, auth, identity = RecordingIdentity()).ensureClientJwt()

        assertIs<UrnetworkJwtBootstrapper.Result.AlreadyPresent>(result)
        assertEquals(0, auth.guestCalls)
        assertEquals(0, auth.clientCalls)
        assertEquals(0, auth.deviceCalls)
        assertEquals("client", store.byClientJwt().first())
    }

    @Test
    fun guestFailureReturnsFailedWhenNoLegacyJwtExists() = runTest {
        val store = InMemoryUrnetworkConfigStore()
        val auth = RecordingAuth(guest = GuestJwtResult.Error("offline"))

        val result = bootstrapper(store, auth, identity = null).ensureClientJwt()

        assertIs<UrnetworkJwtBootstrapper.Result.Failed>(result)
        assertEquals(1, auth.guestCalls)
        assertEquals(0, auth.clientCalls)
        assertNull(store.byJwt().first())
    }

    @Test
    fun clientFailurePersistsGuestJwtButReturnsFailed() = runTest {
        val store = InMemoryUrnetworkConfigStore()
        val auth = RecordingAuth(
            guest = GuestJwtResult.Success("guest-jwt"),
            client = ClientJwtResult.Error("client down"),
        )

        val result = bootstrapper(store, auth, identity = null).ensureClientJwt()

        assertIs<UrnetworkJwtBootstrapper.Result.Failed>(result)
        assertEquals("guest-jwt", store.byJwt().first())
        assertNull(store.byClientJwt().first())
        assertEquals(1, auth.guestCalls)
        assertEquals(1, auth.clientCalls)
    }

    @Test
    fun existingLegacyJwtFallsBackToClientJwtWhenDeviceAuthUnavailable() = runTest {
        val store = InMemoryUrnetworkConfigStore(UrnetworkConfig(byJwt = "legacy"))
        val auth = RecordingAuth(client = ClientJwtResult.Success("client-from-legacy"))

        val result = bootstrapper(store, auth, identity = null).ensureClientJwt()

        assertIs<UrnetworkJwtBootstrapper.Result.Acquired>(result)
        assertEquals("legacy", store.byJwt().first())
        assertEquals("client-from-legacy", store.byClientJwt().first())
        assertEquals(0, auth.guestCalls)
        assertEquals(1, auth.clientCalls)
    }

    @Test
    fun postGuestClientJwtRaceReturnsAlreadyPresentWithoutSecondClientAcquire() = runTest {
        val store = RaceConfigStore()
        val auth = RecordingAuth(guest = GuestJwtResult.Success("guest"))

        val result = bootstrapper(store, auth, identity = null).ensureClientJwt()

        assertIs<UrnetworkJwtBootstrapper.Result.AlreadyPresent>(result)
        assertEquals(1, auth.guestCalls)
        assertEquals(0, auth.clientCalls)
        assertEquals("winner-client", store.byClientJwt().first())
    }

    @Test
    fun defaultDeviceWalletJwtImplementationReturnsErrorAndFallsBackToGuest() = runTest {
        val store = InMemoryUrnetworkConfigStore()
        val auth = object : UrnetworkAuthService {
            var guestCalls = 0
            override suspend fun acquireGuestJwt(): GuestJwtResult {
                guestCalls++
                return GuestJwtResult.Success("guest")
            }

            override suspend fun acquireClientJwt(byJwt: String): ClientJwtResult =
                ClientJwtResult.Success("client")
        }

        val result = bootstrapper(store, auth, identity = RecordingIdentity()).ensureClientJwt()

        assertIs<UrnetworkJwtBootstrapper.Result.Acquired>(result)
        assertEquals("guest", store.byJwt().first())
        assertEquals("client", store.byClientJwt().first())
        assertEquals(1, auth.guestCalls)
    }

    @Test
    fun deviceWalletSuccessPersistsPubkeyNetworkNameAndClientJwt() = runTest {
        val store = InMemoryUrnetworkConfigStore()
        val auth = RecordingAuth(
            client = ClientJwtResult.Success("client-from-device"),
            device = DeviceWalletJwtResult.Success("device-by-jwt", isNewNetwork = true),
        )
        val identity = RecordingIdentity(pubkey = "pub-device")

        val result = bootstrapper(store, auth, identity).ensureClientJwt()

        assertIs<UrnetworkJwtBootstrapper.Result.Acquired>(result)
        assertEquals("device-by-jwt", store.byJwt().first())
        assertEquals("client-from-device", store.byClientJwt().first())
        assertEquals("pub-device", store.devicePubkey().first())
        assertEquals("n-fixed", store.deviceNetworkName().first())
        assertEquals(0, auth.guestCalls)
        assertEquals(1, auth.deviceCalls)
        assertEquals(listOf("n-fixed"), auth.deviceNetworkNames)
    }

    @Test
    fun deviceWalletMigrationRunsEvenWhenLegacyClientJwtAlreadyExists() = runTest {
        val store = InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byJwt = "legacy-by",
                byClientJwt = "legacy-client",
                deviceNetworkName = "  ",
            ),
        )
        val auth = RecordingAuth(
            client = ClientJwtResult.Success("client-after-device"),
            device = DeviceWalletJwtResult.Success("device-by-jwt", isNewNetwork = false),
        )
        val identity = RecordingIdentity(pubkey = "pub-after-device")

        val result = bootstrapper(store, auth, identity).ensureClientJwt()

        assertIs<UrnetworkJwtBootstrapper.Result.Acquired>(result)
        assertEquals("device-by-jwt", store.byJwt().first())
        assertEquals("client-after-device", store.byClientJwt().first())
        assertEquals("pub-after-device", store.devicePubkey().first())
        assertEquals("n-fixed", store.deviceNetworkName().first())
        assertEquals(1, auth.deviceCalls)
        assertEquals(1, auth.clientCalls)
        assertEquals(listOf("n-fixed"), auth.deviceNetworkNames)
    }

    @Test
    fun deviceWalletSuccessFollowedByClientFailurePersistsDeviceJwtOnly() = runTest {
        val store = InMemoryUrnetworkConfigStore()
        val auth = RecordingAuth(
            client = ClientJwtResult.Error("client failed"),
            device = DeviceWalletJwtResult.Success("device-by-jwt", isNewNetwork = true),
        )

        val result = bootstrapper(store, auth, RecordingIdentity()).ensureClientJwt()

        assertIs<UrnetworkJwtBootstrapper.Result.Failed>(result)
        assertEquals("device-by-jwt", store.byJwt().first())
        assertNull(store.byClientJwt().first())
        assertEquals(0, auth.guestCalls)
        assertEquals(1, auth.deviceCalls)
        assertEquals(1, auth.clientCalls)
    }

    @Test
    fun deviceWalletMigrationClearsLegacyClientJwtAndKeepsLegacyJwtWhenPubkeyUnavailable() = runTest {
        val store = InMemoryUrnetworkConfigStore(
            UrnetworkConfig(
                byJwt = "legacy-by",
                byClientJwt = "legacy-client",
                deviceNetworkName = "stored-network",
            ),
        )
        val auth = RecordingAuth(
            client = ClientJwtResult.Success("client-after-migration"),
            device = DeviceWalletJwtResult.Success("device-by", isNewNetwork = false),
        )
        val identity = RecordingIdentity(pubkeyFailure = IllegalStateException("pubkey failed"))

        val result = bootstrapper(store, auth, identity).ensureClientJwt()

        assertIs<UrnetworkJwtBootstrapper.Result.Acquired>(result)
        assertEquals("device-by", store.byJwt().first())
        assertEquals("client-after-migration", store.byClientJwt().first())
        assertNull(store.devicePubkey().first())
        assertEquals("stored-network", store.deviceNetworkName().first())
        assertEquals(0, auth.guestCalls)
        assertEquals(1, auth.deviceCalls)
        assertEquals(listOf("stored-network"), auth.deviceNetworkNames)
    }

    @Test
    fun legacyJwtUsesGuestFallbackWhenDeviceWalletFails() = runTest {
        val store = InMemoryUrnetworkConfigStore(UrnetworkConfig(byJwt = "legacy-by"))
        val auth = RecordingAuth(
            client = ClientJwtResult.Success("client-from-legacy"),
            device = DeviceWalletJwtResult.Error("wallet rejected"),
        )

        val result = bootstrapper(store, auth, RecordingIdentity()).ensureClientJwt()

        assertIs<UrnetworkJwtBootstrapper.Result.Acquired>(result)
        assertEquals("legacy-by", store.byJwt().first())
        assertEquals("client-from-legacy", store.byClientJwt().first())
        assertEquals(0, auth.guestCalls)
        assertEquals(1, auth.deviceCalls)
    }

    @Test
    fun deviceWalletFailureWithoutLegacyJwtFallsBackToGuestFlow() = runTest {
        val store = InMemoryUrnetworkConfigStore()
        val auth = RecordingAuth(
            guest = GuestJwtResult.Success("guest-by"),
            client = ClientJwtResult.Success("client-from-guest"),
            device = DeviceWalletJwtResult.Error("wallet unavailable"),
        )

        val result = bootstrapper(store, auth, RecordingIdentity()).ensureClientJwt()

        assertIs<UrnetworkJwtBootstrapper.Result.Acquired>(result)
        assertEquals("guest-by", store.byJwt().first())
        assertEquals("client-from-guest", store.byClientJwt().first())
        assertEquals(1, auth.deviceCalls)
        assertEquals(1, auth.guestCalls)
        assertEquals(1, auth.clientCalls)
    }

    private fun bootstrapper(
        store: UrnetworkConfigStore,
        auth: UrnetworkAuthService,
        identity: UrnetworkDeviceIdentity?,
    ) = RealUrnetworkJwtBootstrapper(
        configStore = store,
        authService = auth,
        deviceIdentity = identity,
        networkNameGenerator = { "n-fixed" },
    )

    private class RecordingAuth(
        private val guest: GuestJwtResult = GuestJwtResult.Error("not called"),
        private val client: ClientJwtResult = ClientJwtResult.Error("not called"),
        private val device: DeviceWalletJwtResult = DeviceWalletJwtResult.Error("not called"),
    ) : UrnetworkAuthService {
        var guestCalls = 0
        var clientCalls = 0
        var deviceCalls = 0
        val deviceNetworkNames = mutableListOf<String>()

        override suspend fun acquireGuestJwt(): GuestJwtResult {
            guestCalls++
            return guest
        }

        override suspend fun acquireClientJwt(byJwt: String): ClientJwtResult {
            clientCalls++
            return client
        }

        override suspend fun acquireDeviceWalletJwt(
            identity: UrnetworkDeviceIdentity,
            networkName: String,
        ): DeviceWalletJwtResult {
            deviceCalls++
            deviceNetworkNames += networkName
            return device
        }
    }

    private class RecordingIdentity(
        private val pubkey: String = "pub",
        private val pubkeyFailure: Throwable? = null,
    ) : UrnetworkDeviceIdentity {
        override suspend fun pubkeyBase58(): String {
            pubkeyFailure?.let { throw it }
            return pubkey
        }

        override suspend fun sign(message: ByteArray): ByteArray = message.copyOf()
    }

    private class RaceConfigStore : UrnetworkConfigStore {
        private val delegate = InMemoryUrnetworkConfigStore()
        private var updateCalls = 0

        override fun config() = delegate.config()

        override suspend fun update(transform: (UrnetworkConfig) -> UrnetworkConfig) {
            updateCalls++
            delegate.update(transform)
            if (updateCalls == 1) {
                delegate.setByClientJwt("winner-client")
            }
        }
    }
}
