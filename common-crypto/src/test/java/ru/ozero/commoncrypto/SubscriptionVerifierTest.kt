package ru.ozero.commoncrypto

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionVerifierTest {
    private val rfc8032TestPublicKey =
        hexToByteArray(
            "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
        )

    private val rfc8032TestSignature =
        hexToByteArray(
            "e5564300c360ac729086e2cc806e828a" +
                "84877f1eb8e5d974d873e06522490155" +
                "5d369f96f3a028b0cf169ea27814b03d" +
                "e937a05cf7f69e7c0eb5bcb9c87e31db",
        )

    private val rfc8032TestMessage = ByteArray(0)

    @Test
    fun verifyValidSignature() {
        val result =
            SubscriptionVerifier.verify(
                message = rfc8032TestMessage,
                signature = rfc8032TestSignature,
                publicKey = rfc8032TestPublicKey,
            )
        assertTrue(result, "RFC 8032 Test Vector 1 should verify successfully")
    }

    @Test
    fun verifyCorruptedSignature() {
        val corruptedSignature = rfc8032TestSignature.copyOf()
        corruptedSignature[0] = (corruptedSignature[0].toInt() xor 0xFF).toByte()

        val result =
            SubscriptionVerifier.verify(
                message = rfc8032TestMessage,
                signature = corruptedSignature,
                publicKey = rfc8032TestPublicKey,
            )
        assertFalse(result, "Corrupted signature should not verify")
    }

    @Test
    fun verifyWrongKey() {
        val wrongKey =
            hexToByteArray(
                "0000000000000000000000000000000000000000000000000000000000000000",
            )

        val result =
            SubscriptionVerifier.verify(
                message = rfc8032TestMessage,
                signature = rfc8032TestSignature,
                publicKey = wrongKey,
            )
        assertFalse(result, "Wrong public key should not verify")
    }

    @Test
    fun verifyWrongMessage() {
        val wrongMessage = byteArrayOf(0xFF.toByte())

        val result =
            SubscriptionVerifier.verify(
                message = wrongMessage,
                signature = rfc8032TestSignature,
                publicKey = rfc8032TestPublicKey,
            )
        assertFalse(result, "Wrong message should not verify")
    }

    @Test
    fun verifyInvalidSignatureLength() {
        val invalidSignature = ByteArray(63)

        val result =
            SubscriptionVerifier.verify(
                message = rfc8032TestMessage,
                signature = invalidSignature,
                publicKey = rfc8032TestPublicKey,
            )
        assertFalse(result, "Invalid signature length (63 bytes) should return false")
    }

    @Test
    fun verifyInvalidKeyLength() {
        val invalidKey = ByteArray(31)

        val result =
            SubscriptionVerifier.verify(
                message = rfc8032TestMessage,
                signature = rfc8032TestSignature,
                publicKey = invalidKey,
            )
        assertFalse(result, "Invalid key length (31 bytes) should return false")
    }

    private fun hexToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace("\\s".toRegex(), "")
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
