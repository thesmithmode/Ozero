package ru.ozero.enginefptn

import java.security.MessageDigest

data class FptnRuntimeFingerprint(
    private val digest: String,
) {
    override fun toString(): String = "FptnRuntimeFingerprint(digest=***)"
}

fun FptnConfig.runtimeFingerprint(): FptnRuntimeFingerprint =
    FptnRuntimeFingerprint(
        digest = sha256Runtime(
            listOf(
                token,
                selectedServerName.takeUnless { autoSelect }.orEmpty(),
                bypassMethod,
                sniDomain,
                autoSelect.toString(),
            ),
        ),
    )

private fun sha256Runtime(parts: List<String>): String {
    val md = MessageDigest.getInstance("SHA-256")
    parts.forEach { part ->
        md.update(part.toByteArray(Charsets.UTF_8))
        md.update(0.toByte())
    }
    return md.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
