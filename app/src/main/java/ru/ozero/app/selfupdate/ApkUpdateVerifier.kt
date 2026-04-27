package ru.ozero.app.selfupdate

import ru.ozero.commoncrypto.SubscriptionVerifier
import java.io.File

open class ApkUpdateVerifier(
    private val publicKey: ByteArray,
) {

    init {
        require(publicKey.size == ED25519_PUBLIC_KEY_LEN) {
            "publicKey должен быть 32 байта Ed25519, получено ${publicKey.size}"
        }
    }

    open fun verify(apkFile: File, signatureFile: File): Boolean {
        if (!apkFile.exists() || !signatureFile.exists()) return false
        val signature = signatureFile.readBytes()
        if (signature.size != ED25519_SIG_LEN) return false
        return apkFile.inputStream().buffered().use { stream ->
            SubscriptionVerifier.verifyUpdate(stream, signature, publicKey)
        }
    }

    private companion object {
        const val ED25519_PUBLIC_KEY_LEN = 32
        const val ED25519_SIG_LEN = 64
    }
}
