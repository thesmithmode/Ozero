package ru.ozero.enginesingbox

import java.security.MessageDigest

fun String.singboxConfigFingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.take(FINGERPRINT_BYTES).joinToString("") { "%02x".format(it) }
}

private const val FINGERPRINT_BYTES = 6
