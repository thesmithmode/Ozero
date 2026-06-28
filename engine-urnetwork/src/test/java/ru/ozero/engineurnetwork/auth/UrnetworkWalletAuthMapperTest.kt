package ru.ozero.engineurnetwork.auth

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UrnetworkWalletAuthMapperTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUpMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `real auth service rejects blank client jwt before runtime init`() = runTest {
        val service = RealUrnetworkAuthService(Application())

        val result = service.acquireClientJwt("   ")

        assertEquals("byJwt is blank", assertIs<ClientJwtResult.Error>(result).message)
    }

    @Test
    fun `buildWalletAuth signs prefixed pubkey and fills wallet fields`() = runTest {
        val signature = byteArrayOf(1, 2, 3, 4)
        val identity = FakeIdentity(pubkey = "pub-key", signature = signature)

        val auth = UrnetworkWalletAuthMapper.buildWalletAuth(
            identity = identity,
            operation = WalletAuthOperation.LOGIN,
            issuedAtMillis = 123L,
            nonceBytes = { byteArrayOf(9, 8) },
            encodeSignature = { raw -> "encoded:${raw.joinToString("-")}" },
        )

        val expectedMessage = "ozero-auth-v1:pub-key|aud=urnetwork|op=authLogin|iat_ms=123|nonce=encoded:9-8"
        assertEquals("pub-key", auth?.publicKey)
        assertEquals(expectedMessage, auth?.message)
        assertEquals("encoded:1-2-3-4", auth?.signature)
        assertEquals("solana", auth?.blockchain)
        assertEquals(expectedMessage, identity.signedMessage?.decodeToString())
    }

    @Test
    fun `buildWalletAuth returns null when encoder throws`() = runTest {
        val identity = FakeIdentity(pubkey = "pub-key", signature = byteArrayOf(1))

        val auth = UrnetworkWalletAuthMapper.buildWalletAuth(
            identity = identity,
            operation = WalletAuthOperation.LOGIN,
            encodeSignature = { error("encode failed") },
        )

        assertNull(auth)
        assertNull(identity.signedMessage)
    }

    @Test
    fun `buildWalletAuth returns null when pubkey lookup throws`() = runTest {
        val identity = FakeIdentity(pubkeyFailure = IllegalStateException("pubkey failed"))

        val auth = UrnetworkWalletAuthMapper.buildWalletAuth(identity, WalletAuthOperation.LOGIN)

        assertNull(auth)
    }

    @Test
    fun `buildWalletAuth returns null when signing throws`() = runTest {
        val identity = FakeIdentity(pubkey = "pub-key", signFailure = IllegalStateException("sign failed"))

        val auth = UrnetworkWalletAuthMapper.buildWalletAuth(identity, WalletAuthOperation.LOGIN)

        assertNull(auth)
        val signed = identity.signedMessage?.decodeToString().orEmpty()
        assertEquals(true, signed.startsWith("ozero-auth-v1:pub-key|aud=urnetwork|op=authLogin|iat_ms="))
        assertEquals(true, signed.contains("|nonce="))
    }

    @Test
    fun `wallet message binds audience operation timestamp and nonce`() {
        val message = UrnetworkWalletAuthMapper.buildWalletMessage(
            publicKey = "pub-key",
            operation = WalletAuthOperation.NETWORK_CREATE,
            issuedAtMillis = 456L,
            nonce = "nonce",
        )

        assertEquals("ozero-auth-v1:pub-key|aud=urnetwork|op=networkCreate|iat_ms=456|nonce=nonce", message)
    }

    @Test
    fun `default auth service wallet jwt path returns explicit unsupported error`() = runTest {
        val service = object : UrnetworkAuthService {
            override suspend fun acquireGuestJwt(): GuestJwtResult = GuestJwtResult.Error("unused")
            override suspend fun acquireClientJwt(byJwt: String): ClientJwtResult = ClientJwtResult.Error("unused")
        }

        val result = service.acquireDeviceWalletJwt(FakeIdentity(), "network")

        assertEquals(
            "acquireDeviceWalletJwt not implemented",
            assertIs<DeviceWalletJwtResult.Error>(result).message,
        )
    }

    @Test
    fun `mapLoginOutcome maps transport and empty responses to errors`() {
        val transport = UrnetworkWalletAuthMapper.mapLoginOutcome(null, IllegalStateException())
        val transportFallback = UrnetworkWalletAuthMapper.mapLoginOutcome(null, IllegalStateException(null as String?))
        val empty = UrnetworkWalletAuthMapper.mapLoginOutcome(null, null)

        assertEquals("authLogin failed", assertIs<LoginOutcome.Error>(transport).message)
        assertEquals("authLogin failed", assertIs<LoginOutcome.Error>(transportFallback).message)
        assertEquals("empty authLogin response", assertIs<LoginOutcome.Error>(empty).message)
    }

    @Test
    fun `mapLoginOutcomeSnapshot covers wallet login response variants`() {
        val transport = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = "socket closed",
            transportFailed = true,
            responsePresent = false,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = null,
            walletAuthEchoed = false,
        )
        val sdkFallback = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = true,
            sdkErrorMessage = null,
            byJwt = "ignored",
            walletAuthEchoed = true,
        )
        val existing = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = "by-jwt",
            walletAuthEchoed = false,
        )
        val create = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = " ",
            walletAuthEchoed = true,
        )
        val unknown = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = "",
            walletAuthEchoed = false,
        )

        assertEquals("socket closed", assertIs<LoginOutcome.Error>(transport).message)
        assertEquals("authLogin error", assertIs<LoginOutcome.Error>(sdkFallback).message)
        assertEquals("by-jwt", assertIs<LoginOutcome.Existing>(existing).byJwt)
        assertIs<LoginOutcome.NeedCreate>(create)
        assertEquals("unrecognized authLogin response", assertIs<LoginOutcome.Error>(unknown).message)
    }

    @Test
    fun `mapLoginOutcomeSnapshot covers null and blank jwt branches independently`() {
        val nullJwtNeedCreate = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = null,
            walletAuthEchoed = true,
        )
        val blankJwtNeedCreate = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = "\t",
            walletAuthEchoed = true,
        )
        val nullJwtUnknown = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = null,
            walletAuthEchoed = false,
        )

        assertIs<LoginOutcome.NeedCreate>(nullJwtNeedCreate)
        assertIs<LoginOutcome.NeedCreate>(blankJwtNeedCreate)
        assertEquals("unrecognized authLogin response", assertIs<LoginOutcome.Error>(nullJwtUnknown).message)
    }

    @Test
    fun `mapLoginOutcomeSnapshot handles fallback transport error when null`() {
        val transport = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = null,
            transportFailed = true,
            responsePresent = false,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = null,
            walletAuthEchoed = false,
        )
        val create = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = "",
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = "",
            walletAuthEchoed = true,
        )

        assertEquals("authLogin failed", assertIs<LoginOutcome.Error>(transport).message)
        assertIs<LoginOutcome.NeedCreate>(create)
    }

    @Test
    fun `mapLoginOutcomeSnapshot differentiates walletAuthEchoed branch when jwt absent`() {
        val needCreate = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = " ",
            walletAuthEchoed = true,
        )
        val unexpected = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = " ",
            walletAuthEchoed = false,
        )

        assertIs<LoginOutcome.NeedCreate>(needCreate)
        assertEquals("unrecognized authLogin response", assertIs<LoginOutcome.Error>(unexpected).message)
    }

    @Test
    fun `mapLoginOutcomeSnapshot preserves blank transport and sdk error strings`() {
        val blankTransport = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = "",
            transportFailed = true,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = "ignored",
            walletAuthEchoed = true,
        )
        val blankSdkError = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = true,
            sdkErrorMessage = "",
            byJwt = "ignored",
            walletAuthEchoed = true,
        )
        val tabJwt = UrnetworkWalletAuthMapper.mapLoginOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = "\t",
            walletAuthEchoed = false,
        )

        assertEquals("", assertIs<LoginOutcome.Error>(blankTransport).message)
        assertEquals("", assertIs<LoginOutcome.Error>(blankSdkError).message)
        assertEquals("unrecognized authLogin response", assertIs<LoginOutcome.Error>(tabJwt).message)
    }

    @Test
    fun `mapCreateOutcome maps transport and empty responses to errors`() {
        val transport = UrnetworkWalletAuthMapper.mapCreateOutcome(null, IllegalStateException())
        val transportFallback = UrnetworkWalletAuthMapper.mapCreateOutcome(null, IllegalStateException(null as String?))
        val empty = UrnetworkWalletAuthMapper.mapCreateOutcome(null, null)

        assertEquals("networkCreate failed", assertIs<DeviceWalletJwtResult.Error>(transport).message)
        assertEquals("networkCreate failed", assertIs<DeviceWalletJwtResult.Error>(transportFallback).message)
        assertEquals("empty networkCreate response", assertIs<DeviceWalletJwtResult.Error>(empty).message)
    }

    @Test
    fun `mapCreateOutcomeSnapshot covers wallet network creation variants`() {
        val transport = UrnetworkWalletAuthMapper.mapCreateOutcomeSnapshot(
            transportError = "closed",
            transportFailed = true,
            responsePresent = false,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = null,
        )
        val sdkError = UrnetworkWalletAuthMapper.mapCreateOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = true,
            sdkErrorMessage = "denied",
            byJwt = "ignored",
        )
        val sdkFallback = UrnetworkWalletAuthMapper.mapCreateOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = true,
            sdkErrorMessage = null,
            byJwt = null,
        )
        val emptyJwt = UrnetworkWalletAuthMapper.mapCreateOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = " ",
        )
        val success = UrnetworkWalletAuthMapper.mapCreateOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = "new-jwt",
        )

        assertEquals("closed", assertIs<DeviceWalletJwtResult.Error>(transport).message)
        assertEquals("denied", assertIs<DeviceWalletJwtResult.Error>(sdkError).message)
        assertEquals("networkCreate error", assertIs<DeviceWalletJwtResult.Error>(sdkFallback).message)
        assertEquals("networkCreate returned empty jwt", assertIs<DeviceWalletJwtResult.Error>(emptyJwt).message)
        val created = assertIs<DeviceWalletJwtResult.Success>(success)
        assertEquals("new-jwt", created.byJwt)
        assertEquals(true, created.isNewNetwork)
    }

    @Test
    fun `mapCreateOutcomeSnapshot covers null jwt and fallback transport messages`() {
        val nullJwt = UrnetworkWalletAuthMapper.mapCreateOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = null,
        )
        val fallbackTransport = UrnetworkWalletAuthMapper.mapCreateOutcomeSnapshot(
            transportError = null,
            transportFailed = true,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = "ignored",
        )

        assertEquals("networkCreate returned empty jwt", assertIs<DeviceWalletJwtResult.Error>(nullJwt).message)
        assertEquals("networkCreate failed", assertIs<DeviceWalletJwtResult.Error>(fallbackTransport).message)
    }

    @Test
    fun `mapCreateOutcomeSnapshot uses fallback and fallback-by-empty-jwt branches`() {
        val transportFailureFallback = UrnetworkWalletAuthMapper.mapCreateOutcomeSnapshot(
            transportError = null,
            transportFailed = true,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = "ignored",
        )
        val blankJwtFromNetworkCreate = UrnetworkWalletAuthMapper.mapCreateOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = "\n",
        )

        assertEquals(
            "networkCreate failed",
            assertIs<DeviceWalletJwtResult.Error>(transportFailureFallback).message,
        )
        assertEquals(
            "networkCreate returned empty jwt",
            assertIs<DeviceWalletJwtResult.Error>(blankJwtFromNetworkCreate).message,
        )
    }

    @Test
    fun `mapCreateOutcomeSnapshot preserves blank transport sdk errors and whitespace jwt`() {
        val blankTransport = UrnetworkWalletAuthMapper.mapCreateOutcomeSnapshot(
            transportError = "",
            transportFailed = true,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = "ignored",
        )
        val blankSdkError = UrnetworkWalletAuthMapper.mapCreateOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = true,
            sdkErrorMessage = "",
            byJwt = "ignored",
        )
        val tabJwt = UrnetworkWalletAuthMapper.mapCreateOutcomeSnapshot(
            transportError = null,
            transportFailed = false,
            responsePresent = true,
            sdkErrorPresent = false,
            sdkErrorMessage = null,
            byJwt = "\t",
        )

        assertEquals("", assertIs<DeviceWalletJwtResult.Error>(blankTransport).message)
        assertEquals("", assertIs<DeviceWalletJwtResult.Error>(blankSdkError).message)
        assertEquals("networkCreate returned empty jwt", assertIs<DeviceWalletJwtResult.Error>(tabJwt).message)
    }

    private class FakeIdentity(
        private val pubkey: String = "pub",
        private val signature: ByteArray = byteArrayOf(9),
        private val pubkeyFailure: Throwable? = null,
        private val signFailure: Throwable? = null,
    ) : UrnetworkDeviceIdentity {
        var signedMessage: ByteArray? = null

        override suspend fun pubkeyBase58(): String {
            pubkeyFailure?.let { throw it }
            return pubkey
        }

        override suspend fun sign(message: ByteArray): ByteArray {
            signedMessage = message
            signFailure?.let { throw it }
            return signature
        }
    }
}
