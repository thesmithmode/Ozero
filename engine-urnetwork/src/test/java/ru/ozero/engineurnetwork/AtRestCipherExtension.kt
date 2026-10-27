package ru.ozero.engineurnetwork

import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import ru.ozero.commoncrypto.AesGcmAtRestCipher
import ru.ozero.commoncrypto.AtRestSecrets

internal fun installDefaultAtRestCipher() {
    val key = SecretKeySpec(ByteArray(32) { index -> (index + 7).toByte() }, "AES")
    AtRestSecrets.installCipher(AesGcmAtRestCipher(key))
}

class AtRestCipherExtension : BeforeAllCallback {
    override fun beforeAll(context: ExtensionContext) {
        installDefaultAtRestCipher()
    }
}
