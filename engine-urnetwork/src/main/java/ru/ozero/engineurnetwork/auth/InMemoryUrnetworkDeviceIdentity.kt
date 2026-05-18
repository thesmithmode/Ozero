package ru.ozero.engineurnetwork.auth

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

class InMemoryUrnetworkDeviceIdentity(seed: ByteArray? = null) : UrnetworkDeviceIdentity {
    private val sk: Ed25519PrivateKeyParameters

    init {
        val src = seed ?: ByteArray(SEED_LEN).also { SecureRandom().nextBytes(it) }
        require(src.size == SEED_LEN) { "seed must be $SEED_LEN bytes" }
        sk = Ed25519PrivateKeyParameters(src, 0)
    }

    override suspend fun pubkeyBase58(): String = Base58.encode(sk.generatePublicKey().encoded)

    override suspend fun sign(message: ByteArray): ByteArray {
        val signer = Ed25519Signer().apply { init(true, sk) }
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    private companion object {
        const val SEED_LEN = 32
    }
}
