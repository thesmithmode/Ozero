package ru.ozero.commoncrypto

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

object Ed25519Verifier {
    private const val SIG_SIZE = 64
    private const val PUBKEY_SIZE = 32

    fun verify(message: ByteArray, signature: ByteArray, publicKeyRaw32: ByteArray): Boolean {
        if (signature.size != SIG_SIZE) return false
        if (publicKeyRaw32.size != PUBKEY_SIZE) return false
        return runCatching {
            val pubKey = Ed25519PublicKeyParameters(publicKeyRaw32, 0)
            val signer = Ed25519Signer().apply {
                init(false, pubKey)
                update(message, 0, message.size)
            }
            signer.verifySignature(signature)
        }.getOrDefault(false)
    }
}
