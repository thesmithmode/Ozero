package ru.ozero.app.selfupdate

import ru.ozero.commoncrypto.SubscriptionVerifier
import java.io.File

/**
 * Верифицирует скачанный APK Ed25519-подписью. Использует [SubscriptionVerifier] —
 * та же библиотека что и для подписи подписок, единый ключевой материал
 * проекта (E10: рутейтит ключи через docs/key-rotation.md).
 *
 * Поток:
 *   1. apk + apk.sig скачаны во временные файлы
 *   2. apk file → bytes
 *   3. sig file → 64-byte Ed25519 signature
 *   4. publicKey 32 bytes (захардкожен в release-сборке)
 *   5. verify → true → запускаем PackageInstaller. false → удаляем APK, игнорим update.
 */
class ApkUpdateVerifier(
    private val publicKey: ByteArray,
) {

    init {
        require(publicKey.size == ED25519_PUBLIC_KEY_LEN) {
            "publicKey должен быть 32 байта Ed25519, получено ${publicKey.size}"
        }
    }

    fun verify(apkFile: File, signatureFile: File): Boolean {
        if (!apkFile.exists() || !signatureFile.exists()) return false
        val signature = signatureFile.readBytes()
        if (signature.size != ED25519_SIG_LEN) return false
        // Для больших APK (~50 МБ) loading в память приемлемо: одноразовый flow.
        // Альтернатива: streaming через Ed25519Signer.update() chunked — оставлено
        // на оптимизацию когда профайлер покажет проблему.
        val apkBytes = apkFile.readBytes()
        return SubscriptionVerifier.verify(apkBytes, signature, publicKey)
    }

    private companion object {
        const val ED25519_PUBLIC_KEY_LEN = 32
        const val ED25519_SIG_LEN = 64
    }
}
