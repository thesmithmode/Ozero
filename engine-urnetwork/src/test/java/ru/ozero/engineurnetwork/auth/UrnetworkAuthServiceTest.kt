package ru.ozero.engineurnetwork.auth

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UrnetworkAuthServiceTest {

    @Test
    fun `GuestJwtResult Success carries jwt`() {
        val r = GuestJwtResult.Success(byJwt = "eyJabc.def.ghi")
        assertEquals("eyJabc.def.ghi", r.byJwt)
        assertTrue(r.byJwt.isNotBlank())
    }

    @Test
    fun `GuestJwtResult Error carries message`() {
        val r = GuestJwtResult.Error("rate limited")
        assertEquals("rate limited", r.message)
    }

    @Test
    fun `Fake acquireGuestJwt без error возвращает Success с jwt`() = runTest {
        val service = FakeUrnetworkAuthService(jwt = "guest.tok.x")
        val r = service.acquireGuestJwt()
        val ok = assertIs<GuestJwtResult.Success>(r)
        assertEquals("guest.tok.x", ok.byJwt)
    }

    @Test
    fun `Fake acquireGuestJwt с error возвращает Error`() = runTest {
        val service = FakeUrnetworkAuthService(error = "throttled")
        val r = service.acquireGuestJwt()
        val err = assertIs<GuestJwtResult.Error>(r)
        assertEquals("throttled", err.message)
    }

    @Test
    fun `Fake acquireGuestJwt с пустым jwt возвращает Error`() = runTest {
        val service = FakeUrnetworkAuthService(jwt = "")
        val r = service.acquireGuestJwt()
        val err = assertIs<GuestJwtResult.Error>(r)
        assertTrue(err.message.isNotBlank())
    }

    @Test
    fun `ClientJwtResult Success carries client jwt`() {
        val r = ClientJwtResult.Success(byClientJwt = "client.jwt")
        assertEquals("client.jwt", r.byClientJwt)
    }

    @Test
    fun `Fake acquireClientJwt без error возвращает Success`() = runTest {
        val service = FakeUrnetworkAuthService(clientJwt = "client.tok.x")
        val r = service.acquireClientJwt("g")
        val ok = assertIs<ClientJwtResult.Success>(r)
        assertEquals("client.tok.x", ok.byClientJwt)
    }

    @Test
    fun `Fake acquireClientJwt с пустым byJwt возвращает Error`() = runTest {
        val service = FakeUrnetworkAuthService()
        val r = service.acquireClientJwt("")
        assertIs<ClientJwtResult.Error>(r)
    }

    @Test
    fun `default device wallet jwt returns explicit not implemented error`() = runTest {
        val service = FakeUrnetworkAuthService()
        val r = service.acquireDeviceWalletJwt(
            identity = InMemoryUrnetworkDeviceIdentity(ByteArray(32) { it.toByte() }),
            networkName = "net",
        )
        val err = assertIs<DeviceWalletJwtResult.Error>(r)
        assertTrue(err.message.contains("not implemented"))
    }

    @Test
    fun `DeviceWalletJwtResult Success carries jwt and new network flag`() {
        val existing = DeviceWalletJwtResult.Success(byJwt = "wallet.jwt", isNewNetwork = false)
        val created = DeviceWalletJwtResult.Success(byJwt = "created.jwt", isNewNetwork = true)

        assertEquals("wallet.jwt", existing.byJwt)
        assertEquals(false, existing.isNewNetwork)
        assertEquals("created.jwt", created.byJwt)
        assertEquals(true, created.isNewNetwork)
    }

    @Test
    fun `UrnetworkDeviceIdentity defaults do not export or import seed`() = runTest {
        val identity = object : UrnetworkDeviceIdentity {
            override suspend fun pubkeyBase58(): String = "pub"
            override suspend fun sign(message: ByteArray): ByteArray = message.reversedArray()
        }

        assertEquals(null, identity.exportSeedForBackup())
        assertEquals(false, identity.importSeedFromBackup(byteArrayOf(1, 2, 3)))
        assertEquals("pub", identity.pubkeyBase58())
        assertEquals(listOf(3, 2, 1), identity.sign(byteArrayOf(1, 2, 3)).map { it.toInt() })
    }

    private class FakeUrnetworkAuthService(
        private val jwt: String = "fake.jwt",
        private val clientJwt: String = "fake.cjwt",
        private val error: String? = null,
    ) : UrnetworkAuthService {
        override suspend fun acquireGuestJwt(): GuestJwtResult = when {
            error != null -> GuestJwtResult.Error(error)
            jwt.isBlank() -> GuestJwtResult.Error("empty jwt from server")
            else -> GuestJwtResult.Success(byJwt = jwt)
        }
        override suspend fun acquireClientJwt(byJwt: String): ClientJwtResult = when {
            byJwt.isBlank() -> ClientJwtResult.Error("byJwt blank")
            error != null -> ClientJwtResult.Error(error)
            clientJwt.isBlank() -> ClientJwtResult.Error("empty client jwt")
            else -> ClientJwtResult.Success(byClientJwt = clientJwt)
        }
    }
}
