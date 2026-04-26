package ru.ozero.commoncrypto

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.InputStream

/**
 * Ed25519 verifier для подписей подписок и self-update APK.
 * Сам [Ed25519Signer.verifySignature] работает за константное время (BC реализация),
 * ранние возвраты по длине параметров — публичная информация, не утечка.
 *
 * **Domain separation**: подпись над `<domain-prefix> || message`, где domain отличает
 * bootstrap snapshot от self-update payload. Без prefix один Ed25519 ключ для двух доменов
 * допускает cross-protocol attack: валидная подпись от bootstrap принимается self-update
 * verifier'ом и наоборот.
 *
 * [verify] оставлен для backward-compatibility с RFC 8032 test-vectors. В продакшене
 * используйте [verifyBootstrap] / [verifyUpdate].
 *
 * Намеренно swallow [IllegalArgumentException] (невалидная точка кривой / битые BC-параметры)
 * и [org.bouncycastle.crypto.DataLengthException] (битая длина): для крипто-верификации
 * любой невалидный input маппится в `false`. Логирование запрещено — потенциальная утечка
 * деталей входа и timing-сигнал. Стандартная практика для крипто-обёрток (Tink, Conscrypt).
 */
object SubscriptionVerifier {

    private val BOOTSTRAP_DOMAIN = "ozero.bootstrap.v1:".toByteArray(Charsets.UTF_8)
    private val UPDATE_DOMAIN = "ozero.update.v1:".toByteArray(Charsets.UTF_8)
    private const val STREAM_CHUNK_BYTES = 64 * 1024

    /** Bootstrap snapshot подпись. Domain prefix защищает от cross-protocol re-use. */
    fun verifyBootstrap(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean =
        verify(BOOTSTRAP_DOMAIN + message, signature, publicKey)

    /** Self-update payload подпись. Отдельный domain от bootstrap. */
    fun verifyUpdate(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean =
        verify(UPDATE_DOMAIN + message, signature, publicKey)

    /**
     * Streaming overload — для больших APK (десятки/сотни МБ): не загружает весь файл
     * в heap, читает чанками. Caller отвечает за close [messageStream].
     */
    @Suppress("SwallowedException")
    fun verifyUpdate(messageStream: InputStream, signature: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != 64 || publicKey.size != 32) return false
        return try {
            val signer = Ed25519Signer()
            signer.init(false, Ed25519PublicKeyParameters(publicKey))
            signer.update(UPDATE_DOMAIN, 0, UPDATE_DOMAIN.size)
            val buf = ByteArray(STREAM_CHUNK_BYTES)
            while (true) {
                val n = messageStream.read(buf)
                if (n < 0) break
                if (n > 0) signer.update(buf, 0, n)
            }
            signer.verifySignature(signature)
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: org.bouncycastle.crypto.DataLengthException) {
            false
        } catch (e: java.io.IOException) {
            false
        } catch (e: RuntimeException) {
            false
        }
    }

    @Suppress("SwallowedException")
    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != 64 || publicKey.size != 32) return false
        return try {
            val params = Ed25519PublicKeyParameters(publicKey)
            val signer = Ed25519Signer()
            signer.init(false, params)
            signer.update(message, 0, message.size)
            signer.verifySignature(signature)
        } catch (e: IllegalArgumentException) {
            // Невалидная точка на кривой / некорректные параметры BC.
            false
        } catch (e: org.bouncycastle.crypto.DataLengthException) {
            false
        } catch (e: RuntimeException) {
            // Защита от любых других непредвиденных RuntimeException из BC.
            false
        }
    }
}
