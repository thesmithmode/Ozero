package ru.ozero.engineurnetwork.auth

import android.app.Application
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import ru.ozero.enginescore.PersistentLoggers
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class RealUrnetworkDeviceIdentity(
    private val app: Application,
) : UrnetworkDeviceIdentity {

    private val mutex = Mutex()

    @Volatile
    private var loaded: Ed25519PrivateKeyParameters? = null

    override suspend fun pubkeyBase58(): String = withContext(Dispatchers.IO) {
        val pk = ensureLoaded().generatePublicKey().encoded
        Base58.encode(pk)
    }

    override suspend fun sign(message: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val sk = ensureLoaded()
        val signer = Ed25519Signer().apply { init(true, sk) }
        signer.update(message, 0, message.size)
        signer.generateSignature()
    }

    private suspend fun ensureLoaded(): Ed25519PrivateKeyParameters {
        loaded?.let { return it }
        return mutex.withLock {
            loaded?.let { return@withLock it }
            val seed = loadOrGenerateSeed()
            Ed25519PrivateKeyParameters(seed, 0).also { loaded = it }
        }
    }

    private fun loadOrGenerateSeed(): ByteArray {
        val dir = File(app.filesDir, KEYPAIR_DIR).apply { if (!exists()) mkdirs() }
        val file = File(dir, KEYPAIR_FILE)
        if (file.exists() && file.length() > 0L) {
            val seed = runCatching { readEncrypted(file) }.getOrNull()
            if (seed != null && seed.size == ED25519_SEED_LEN) {
                Log.i(TAG, "device identity loaded — Ed25519 seed restored from disk")
                return seed
            }
            PersistentLoggers.warn(TAG, "device identity file corrupt — regenerating keypair")
        }
        val seed = ByteArray(ED25519_SEED_LEN).also { SecureRandom().nextBytes(it) }
        runCatching { writeEncrypted(file, seed) }
            .onFailure { PersistentLoggers.warn(TAG, "writeEncrypted threw: ${it.message}") }
        Log.i(TAG, "device identity generated — Ed25519 seed persisted")
        return seed
    }

    private fun writeEncrypted(file: File, seed: ByteArray) {
        val key = getOrCreateAesKey()
        val cipher = Cipher.getInstance(CIPHER_ALG).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
        val iv = cipher.iv
        if (iv.size !in MIN_IV_LEN..MAX_IV_LEN) error("AndroidKeyStore IV size ${iv.size} out of bounds")
        val ct = cipher.doFinal(seed)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().use { out ->
            out.write(iv.size)
            out.write(iv)
            out.write(ct)
        }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            error("device-keypair rename failed")
        }
    }

    private fun readEncrypted(file: File): ByteArray {
        val bytes = file.readBytes()
        if (bytes.size < MIN_FILE_BYTES) error("file too short: ${bytes.size} < $MIN_FILE_BYTES")
        val ivLen = bytes[0].toInt() and 0xff
        if (ivLen !in MIN_IV_LEN..MAX_IV_LEN) error("bad iv len=$ivLen")
        if (bytes.size < 1 + ivLen + GCM_TAG_BYTES) error("ct shorter than GCM tag")
        val iv = bytes.copyOfRange(1, 1 + ivLen)
        val ct = bytes.copyOfRange(1 + ivLen, bytes.size)
        val key = getOrCreateAesKey()
        val cipher = Cipher.getInstance(CIPHER_ALG).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val seed = cipher.doFinal(ct)
        if (seed.size != ED25519_SEED_LEN) error("decrypted seed size ${seed.size} != $ED25519_SEED_LEN")
        return seed
    }

    private fun getOrCreateAesKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        kg.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_BITS)
                .build(),
        )
        return kg.generateKey()
    }

    private companion object {
        const val TAG = "RealUrnetworkDeviceIdentity"
        const val KEYPAIR_DIR = "urnetwork"
        const val KEYPAIR_FILE = "device-keypair.bin"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "ozero_urn_dev_v1"
        const val CIPHER_ALG = "AES/GCM/NoPadding"
        const val ED25519_SEED_LEN = 32
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        const val MIN_IV_LEN = 12
        const val MAX_IV_LEN = 16
        const val MIN_FILE_BYTES = 1 + MIN_IV_LEN + ED25519_SEED_LEN + GCM_TAG_BYTES
        const val AES_KEY_BITS = 256
    }
}
