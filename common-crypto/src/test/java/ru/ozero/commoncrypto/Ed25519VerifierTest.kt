package ru.ozero.commoncrypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Ed25519VerifierTest {

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
    fun `good signature verifies as true`() {
        val (priv, pub) = keypair()
        val msg = "hello ozero".toByteArray()
        val sig = sign(priv, msg)
        assertTrue(Ed25519Verifier.verify(msg, sig, pub))
    }

    @Test
    fun `flipped bit in message returns false`() {
        val (priv, pub) = keypair()
        val msg = "hello ozero".toByteArray()
        val sig = sign(priv, msg)
        val tampered = msg.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertFalse(Ed25519Verifier.verify(tampered, sig, pub))
    }

    @Test
    fun `flipped bit in signature returns false`() {
        val (priv, pub) = keypair()
        val msg = "hello ozero".toByteArray()
        val sig = sign(priv, msg).copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertFalse(Ed25519Verifier.verify(msg, sig, pub))
    }

    @Test
    fun `wrong public key returns false`() {
        val (priv, _) = keypair(seed = 1L)
        val (_, otherPub) = keypair(seed = 2L)
        val msg = "hello ozero".toByteArray()
        val sig = sign(priv, msg)
        assertFalse(Ed25519Verifier.verify(msg, sig, otherPub))
    }

    @Test
    fun `wrong signature length returns false without throwing`() {
        val (_, pub) = keypair()
        val msg = "hello ozero".toByteArray()
        assertFalse(Ed25519Verifier.verify(msg, ByteArray(63), pub))
        assertFalse(Ed25519Verifier.verify(msg, ByteArray(0), pub))
        assertFalse(Ed25519Verifier.verify(msg, ByteArray(128), pub))
    }

    @Test
    fun `wrong public key length returns false without throwing`() {
        val (priv, _) = keypair()
        val msg = "hello ozero".toByteArray()
        val sig = sign(priv, msg)
        assertFalse(Ed25519Verifier.verify(msg, sig, ByteArray(31)))
        assertFalse(Ed25519Verifier.verify(msg, sig, ByteArray(0)))
        assertFalse(Ed25519Verifier.verify(msg, sig, ByteArray(64)))
    }

    @Test
    fun `empty message with valid signature still works`() {
        val (priv, pub) = keypair()
        val msg = ByteArray(0)
        val sig = sign(priv, msg)
        assertTrue(Ed25519Verifier.verify(msg, sig, pub))
    }
}
