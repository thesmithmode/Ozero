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
open class ApkUpdateVerifier(
    private val publicKey: ByteArray,
) {

    init {
        require(publicKey.size == ED25519_PUBLIC_KEY_LEN) {
            "publicKey должен быть 32 байта Ed25519, получено ${publicKey.size}"
        }
    }

    open fun verify(apkFile: File, signatureFile: File): Boolean {
        if (!apkFile.exists() || !signatureFile.exists()) return false
        val signature = signatureFile.readBytes()
        if (signature.size != ED25519_SIG_LEN) return false
        // Streaming verify: 200 МБ APK в один heap-allocation = OOM на бюджетных
        // устройствах. SubscriptionVerifier.verifyUpdate(InputStream) читает 64KB чанками.
        // Domain-prefix "ozero.update.v1:" — защита от cross-protocol подмены подписей.
        return apkFile.inputStream().buffered().use { stream ->
            SubscriptionVerifier.verifyUpdate(stream, signature, publicKey)
        }
    }

    private companion object {
        const val ED25519_PUBLIC_KEY_LEN = 32
        const val ED25519_SIG_LEN = 64
    }
}
