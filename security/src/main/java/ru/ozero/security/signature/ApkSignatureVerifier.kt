package ru.ozero.security.signature

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Runtime APK signature verification (anti-repackaging).
 *
 * Сравнивает SHA-256 текущей подписи APK с ожидаемым значением, прошитым в коде
 * release-сборки. Если злоумышленник пересоберёт APK со своим ключом — hash
 * не совпадёт и [isSelfSignatureValid] вернёт false. Combine с string encryption
 * (R8 obfuscator) чтобы expected hash не было легко поменять hex-редактором.
 */
class ApkSignatureVerifier(
    private val context: Context,
    private val expectedSha256Hex: String,
) {

    fun isSelfSignatureValid(): Boolean {
        val sigs = loadSignatures() ?: return false
        return sigs.any { sha256(it).equals(expectedSha256Hex, ignoreCase = true) }
    }

    /** Текущий fingerprint — для логов / debug release setup. */
    fun currentSignatureSha256(): String? = loadSignatures()?.firstOrNull()?.let(::sha256)

    private fun loadSignatures(): List<ByteArray>? {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES,
                )
                val sigInfo = info.signingInfo ?: return null
                if (sigInfo.hasMultipleSigners()) {
                    sigInfo.apkContentsSigners.map { it.toByteArray() }
                } else {
                    sigInfo.signingCertificateHistory.map { it.toByteArray() }
                }
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures?.map { it.toByteArray() }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            for (b in digest) append("%02x".format(b))
        }
    }
}
