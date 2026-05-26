package ru.ozero.corebackup

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object BackupCipher {

    private val MAGIC = byteArrayOf(0x4F, 0x5A, 0x52, 0x42) // "OZRB"
    private const val FORMAT_VERSION: Byte = 0x01
    private const val NONCE_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val MIN_ENCRYPTED_SIZE = 4 + 1 + NONCE_LEN + GCM_TAG_BITS / 8 // 33

    // Ключ хардкожен намеренно: one-click cross-device restore без аккаунта/сервера
    private val key: SecretKeySpec by lazy {
        val raw = MessageDigest.getInstance("SHA-256")
            .digest("ru.ozero.app:bkp-v1:hf7k2m9x".toByteArray(Charsets.UTF_8))
        SecretKeySpec(raw, "AES")
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArrayOutputStream(MAGIC.size + 1 + NONCE_LEN + ciphertext.size).apply {
            write(MAGIC)
            write(byteArrayOf(FORMAT_VERSION))
            write(nonce)
            write(ciphertext)
        }.toByteArray()
    }

    fun decrypt(data: ByteArray): ByteArray {
        require(data.size >= MIN_ENCRYPTED_SIZE) { "Backup data too short" }
        require(data.copyOf(MAGIC.size).contentEquals(MAGIC)) { "Invalid backup magic" }
        val nonce = data.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + NONCE_LEN)
        val ciphertext = data.copyOfRange(MAGIC.size + 1 + NONCE_LEN, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher.doFinal(ciphertext)
    }

    fun isEncrypted(data: ByteArray): Boolean =
        data.size >= MAGIC.size && data.copyOf(MAGIC.size).contentEquals(MAGIC)
}
