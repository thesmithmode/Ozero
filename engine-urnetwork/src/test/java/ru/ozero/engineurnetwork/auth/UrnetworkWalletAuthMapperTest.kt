package ru.ozero.engineurnetwork.auth

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UrnetworkWalletAuthMapperTest {

    @Test
    fun `buildWalletAuth returns null when pubkey lookup throws`() = runTest {
        val identity = FakeIdentity(pubkeyFailure = IllegalStateException("pubkey failed"))

        val auth = UrnetworkWalletAuthMapper.buildWalletAuth(identity)

        assertNull(auth)
    }

    @Test
    fun `buildWalletAuth returns null when signing throws`() = runTest {
        val identity = FakeIdentity(pubkey = "pub-key", signFailure = IllegalStateException("sign failed"))

        val auth = UrnetworkWalletAuthMapper.buildWalletAuth(identity)

        assertNull(auth)
        assertEquals("ozero-auth-v1:pub-key", identity.signedMessage?.decodeToString())
    }

    @Test
    fun `mapLoginOutcome maps transport and empty responses to errors`() {
        val transport = UrnetworkWalletAuthMapper.mapLoginOutcome(null, IllegalStateException())
        val empty = UrnetworkWalletAuthMapper.mapLoginOutcome(null, null)

        assertEquals("authLogin failed", assertIs<LoginOutcome.Error>(transport).message)
        assertEquals("empty authLogin response", assertIs<LoginOutcome.Error>(empty).message)
    }

    @Test
    fun `mapCreateOutcome maps transport and empty responses to errors`() {
        val transport = UrnetworkWalletAuthMapper.mapCreateOutcome(null, IllegalStateException())
        val empty = UrnetworkWalletAuthMapper.mapCreateOutcome(null, null)

        assertEquals("networkCreate failed", assertIs<DeviceWalletJwtResult.Error>(transport).message)
        assertEquals("empty networkCreate response", assertIs<DeviceWalletJwtResult.Error>(empty).message)
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
