package ru.ozero.commoncrypto

import org.bouncycastle.util.encoders.Base64

object AtRestSecrets {
    const val PREFIX = "enc1:"

    @Volatile
    private var override: AtRestCipher? = null

    @Volatile
    private var cached: AtRestCipher? = null

    fun installCipher(cipher: AtRestCipher) {
        override = cipher
    }

    fun cipher(): AtRestCipher {
        override?.let { return it }
        cached?.let { return it }
        return synchronized(this) {
            override ?: cached ?: AndroidKeystoreAtRest.cipher().also { cached = it }
        }
    }
}

object PreferenceAtRest {
    fun isSealed(value: String): Boolean = value.startsWith(AtRestSecrets.PREFIX)

    fun seal(plaintext: String): String {
        if (isSealed(plaintext)) return plaintext
        val payload = AtRestSecrets.cipher().encrypt(plaintext.toByteArray(Charsets.UTF_8))
        return AtRestSecrets.PREFIX + Base64.toBase64String(payload)
    }

    fun open(stored: String): String {
        if (!isSealed(stored)) return stored
        val body = stored.substring(AtRestSecrets.PREFIX.length)
        val payload = decode(body)
        return AtRestSecrets.cipher().decrypt(payload).toString(Charsets.UTF_8)
    }

    private fun decode(body: String): ByteArray {
        try {
            return Base64.decode(body)
        } catch (e: Exception) {
            throw IllegalArgumentException("at-rest secret is corrupt", e)
        }
    }
}
