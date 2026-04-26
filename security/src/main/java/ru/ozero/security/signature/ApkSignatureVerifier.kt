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

    // Намеренно swallow NameNotFoundException → null (own package всегда существует;
    // edge case в тестах/изменённой среде маппится в "подпись не доступна").
    // Логирование избыточно — caller обрабатывает null.
    @Suppress("SwallowedException")
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
                // Используем ТЕКУЩИЕ подписи (apkContentsSigners) для обеих веток.
                // signingCertificateHistory пропустит ротированные (потенциально
                // скомпрометированные) ключи как валидные — это противоречит anti-repackaging.
                sigInfo.apkContentsSigners.map { it.toByteArray() }
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)

                @Suppress("DEPRECATION")
                val sigs = info.signatures
                // API 24-27: GET_SIGNATURES vulnerable к Janus / multi-signer bypass.
                // Множественные signers = подозрительный паттерн (легитимный release одиночный).
                // Fail-closed: считаем подпись невалидной если signers != 1.
                if (sigs == null || sigs.size != 1) null else sigs.map { it.toByteArray() }
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
