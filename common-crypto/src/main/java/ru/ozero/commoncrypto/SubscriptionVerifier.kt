package ru.ozero.commoncrypto

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Ed25519 verifier для подписей подписок и self-update APK.
 * Сам [Ed25519Signer.verifySignature] работает за константное время (BC реализация),
 * ранние возвраты по длине параметров — публичная информация, не утечка.
 *
 * Намеренно swallow [IllegalArgumentException] (невалидная точка кривой / битые BC-параметры)
 * и [org.bouncycastle.crypto.DataLengthException] (битая длина): для крипто-верификации
 * любой невалидный input маппится в `false`. Логирование запрещено — потенциальная утечка
 * деталей входа и timing-сигнал. Стандартная практика для крипто-обёрток (Tink, Conscrypt).
 */
object SubscriptionVerifier {
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
        }
    }
}
