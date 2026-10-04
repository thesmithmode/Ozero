package ru.ozero.commoncrypto

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface AtRestCipher {
    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(payload: ByteArray): ByteArray
}

class AesGcmAtRestCipher(
    private val key: SecretKey,
) : AtRestCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        check(iv != null && iv.size == IV_LEN)
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    override fun decrypt(payload: ByteArray): ByteArray {
        require(payload.size > IV_LEN + TAG_LEN)
        val iv = payload.copyOfRange(0, IV_LEN)
        val ciphertext = payload.copyOfRange(IV_LEN, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val TAG_LEN = 16
        const val TAG_BITS = 128
    }
}
