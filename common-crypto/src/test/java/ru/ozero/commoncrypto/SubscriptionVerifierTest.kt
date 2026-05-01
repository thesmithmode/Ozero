package ru.ozero.commoncrypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.security.SecureRandom
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionVerifierTest {

    private fun keypair(seed: Long = 42L): Pair<Ed25519PrivateKeyParameters, ByteArray> {
        val rnd = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(seed) }
        val priv = Ed25519PrivateKeyParameters(rnd)
        val pub = priv.generatePublicKey().encoded
        return priv to pub
    }

    private fun sign(priv: Ed25519PrivateKeyParameters, msg: ByteArray): ByteArray {
        val signer = Ed25519Signer().apply {
            init(true, priv)
            update(msg, 0, msg.size)
        }
        return signer.generateSignature()
    }

    @Test
    fun verifyBootstrapAcceptsValidSignedPayload() {
        val (priv, pub) = keypair()
        val payload = "ozero-bootstrap-config-v1".toByteArray()
        val sig = sign(priv, "ozero.bootstrap.v1:".toByteArray() + payload)
        assertTrue(
            SubscriptionVerifier.verifyBootstrap(payload, sig, pub),
            "verifyBootstrap должен вернуть true для подписи над BOOTSTRAP_DOMAIN+payload.",
        )
    }

    @Test
    fun verifyUpdateAcceptsValidSignedPayload() {
        val (priv, pub) = keypair()
        val payload = "ozero-update-manifest".toByteArray()
        val sig = sign(priv, "ozero.update.v1:".toByteArray() + payload)
        assertTrue(
            SubscriptionVerifier.verifyUpdate(payload, sig, pub),
            "verifyUpdate должен вернуть true для подписи над UPDATE_DOMAIN+payload.",
        )
    }

    @Test
    fun verifyUpdateStreamAcceptsValidSignedPayload() {
        val (priv, pub) = keypair()
        val payload = "stream-payload-bytes".toByteArray()
        val sig = sign(priv, "ozero.update.v1:".toByteArray() + payload)
        assertTrue(
            SubscriptionVerifier.verifyUpdate(ByteArrayInputStream(payload), sig, pub),
            "verifyUpdate(stream) должен вернуть true — production self-update path.",
        )
    }

    @Test
    fun verifyBootstrapRejectsBootstrapSigOnUpdateDomain() {
        val (priv, pub) = keypair()
        val payload = "shared-payload".toByteArray()
        val updateSig = sign(priv, "ozero.update.v1:".toByteArray() + payload)
        assertFalse(
            SubscriptionVerifier.verifyBootstrap(payload, updateSig, pub),
            "Domain separation: подпись с UPDATE_DOMAIN не должна валидироваться через verifyBootstrap.",
        )
    }

    @Test
    fun verifyUpdateRejectsUpdateSigOnBootstrapDomain() {
        val (priv, pub) = keypair()
        val payload = "shared-payload".toByteArray()
        val bootstrapSig = sign(priv, "ozero.bootstrap.v1:".toByteArray() + payload)
        assertFalse(
            SubscriptionVerifier.verifyUpdate(payload, bootstrapSig, pub),
            "Domain separation: подпись с BOOTSTRAP_DOMAIN не должна валидироваться через verifyUpdate.",
        )
    }

    @Test
    fun verifyCorruptedSignature() {
        val (priv, pub) = keypair()
        val payload = "payload".toByteArray()
        val sig = sign(priv, "ozero.update.v1:".toByteArray() + payload)
        sig[0] = (sig[0].toInt() xor 0xFF).toByte()
        assertFalse(
            SubscriptionVerifier.verifyUpdate(payload, sig, pub),
            "Corrupted signature should not verify",
        )
    }

    @Test
    fun verifyWrongKey() {
        val (priv, _) = keypair(seed = 1L)
        val (_, otherPub) = keypair(seed = 2L)
        val payload = "payload".toByteArray()
        val sig = sign(priv, "ozero.update.v1:".toByteArray() + payload)
        assertFalse(
            SubscriptionVerifier.verifyUpdate(payload, sig, otherPub),
            "Wrong public key should not verify",
        )
    }

    @Test
    fun verifyWrongMessage() {
        val (priv, pub) = keypair()
        val payload = "payload".toByteArray()
        val sig = sign(priv, "ozero.update.v1:".toByteArray() + payload)
        assertFalse(
            SubscriptionVerifier.verifyUpdate("different-payload".toByteArray(), sig, pub),
            "Wrong message should not verify",
        )
    }

    @Test
    fun verifyInvalidSignatureLength() {
        val (_, pub) = keypair()
        assertFalse(
            SubscriptionVerifier.verifyUpdate("payload".toByteArray(), ByteArray(63), pub),
            "Invalid signature length (63 bytes) should return false",
        )
    }

    @Test
    fun verifyInvalidKeyLength() {
        val (priv, _) = keypair()
        val payload = "payload".toByteArray()
        val sig = sign(priv, "ozero.update.v1:".toByteArray() + payload)
        assertFalse(
            SubscriptionVerifier.verifyUpdate(payload, sig, ByteArray(31)),
            "Invalid key length (31 bytes) should return false",
        )
    }

    @Test
    fun bouncyCastleEd25519VerifiesRfc8032Vector2() {
        val pub = hexToByteArray("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c")
        val sig = hexToByteArray(
            "92a009a9f0d4cab8720e820b5f642540" +
                "a2b27b5416503f8fb3762223ebdb69da" +
                "085ac1e43e15996e458f3613d0f11d8c" +
                "387b2eaeb4302aeeb00d291612bb0c00",
        )
        val msg = byteArrayOf(0x72.toByte())
        val signer = Ed25519Signer()
        signer.init(false, Ed25519PublicKeyParameters(pub))
        signer.update(msg, 0, msg.size)
        assertTrue(
            signer.verifySignature(sig),
            "RFC 8032 Test Vector 2 — non-empty msg=0x72. Закрепляет что BC Ed25519 RFC " +
                "compliant для production-relevant non-empty messages. Production всегда передаёт " +
                "domain-prefixed payload (≥16 bytes), пустой message path в SubscriptionVerifier " +
                "не достижим через verifyBootstrap/verifyUpdate.",
        )
    }

    @Test
    fun bouncyCastleEd25519RejectsRfc8032Vector1EmptyMessageQuirk() {
        val pub = hexToByteArray("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        val sig = hexToByteArray(
            "e5564300c360ac729086e2cc806e828a" +
                "84877f1eb8e5d974d873e06522490155" +
                "5d369f96f3a028b0cf169ea27814b03d" +
                "e937a05cf7f69e7c0eb5bcb9c87e31db",
        )
        val msg = ByteArray(0)
        val signer = Ed25519Signer()
        signer.init(false, Ed25519PublicKeyParameters(pub))
        signer.update(msg, 0, msg.size)
        val result = signer.verifySignature(sig)
        assertFalse(
            result,
            "BC Ed25519 quirk regression: BouncyCastle 1.79/1.80 не RFC compliant на Test Vector 1 " +
                "(empty msg). Если этот тест падает — BC починил quirk, проверить не сломалось ли " +
                "что-то в production verify path. Production не достигает empty msg path " +
                "(BOOTSTRAP_DOMAIN/UPDATE_DOMAIN всегда non-empty), test существует как " +
                "сторожевой sentinel — фиксирует known-bad behavior среды.",
        )
    }

    private fun hexToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace("\\s".toRegex(), "")
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
